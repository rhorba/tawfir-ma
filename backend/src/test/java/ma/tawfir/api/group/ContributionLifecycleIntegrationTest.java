package ma.tawfir.api.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import ma.tawfir.api.TestcontainersConfiguration;
import ma.tawfir.api.auth.OtpChallengeRepository;
import ma.tawfir.api.auth.entity.OtpChallenge;
import ma.tawfir.api.common.PhoneNumberCodec;
import ma.tawfir.api.group.entity.ContributionSchedule;
import ma.tawfir.api.group.entity.ContributionStatus;
import ma.tawfir.api.ledger.LedgerEntryRepository;
import ma.tawfir.api.ledger.entity.LedgerEntry;
import ma.tawfir.api.ledger.entity.LedgerSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Full-stack: real Postgres (Testcontainers) — covers stories 3.1/3.2's
 * Gherkin scenario end-to-end plus the test-strategy-tawfir.md §4 items that
 * only a real DB can verify: the append-only trigger and race conditions.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ContributionLifecycleIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private OtpChallengeRepository otpChallengeRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private ContributionScheduleRepository contributionScheduleRepository;
	@Autowired
	private LedgerEntryRepository ledgerEntryRepository;
	@Autowired
	private ContributionService contributionService;
	@Autowired
	private LateContributionScheduler lateContributionScheduler;
	@Autowired
	private EntityManager entityManager;
	@Autowired
	private PlatformTransactionManager transactionManager;
	@Autowired
	private PhoneNumberCodec phoneNumberCodec;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void cleanOtpChallenges() {
		otpChallengeRepository.deleteAll();
	}

	private String loginAndGetAccessToken(String phoneNumber) throws Exception {
		String code = "654321";
		otpChallengeRepository.save(
			new OtpChallenge(phoneNumberCodec.hash(phoneNumber), passwordEncoder.encode(code), Instant.now().plusSeconds(300)));

		MvcResult result = mockMvc.perform(post("/api/v1/auth/otp/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"phoneNumber\":\"" + phoneNumber + "\",\"code\":\"" + code + "\"}"))
			.andExpect(status().isOk())
			.andReturn();

		return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
	}

	private JsonNode createAndFinalizeGroup(String organizerToken, String organizerPhone, String memberPhone) throws Exception {
		String createBody = """
			{"name":"Contrib Test","contributionAmount":200,"frequency":"MONTHLY","totalCycles":2,
			"payoutOrderMode":"MANUAL","members":["%s","%s"]}
			""".formatted(organizerPhone, memberPhone);

		MvcResult createResult = mockMvc.perform(post("/api/v1/groups")
				.header("Authorization", "Bearer " + organizerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(createBody))
			.andExpect(status().isCreated())
			.andReturn();
		String groupId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

		MvcResult finalizeResult = mockMvc.perform(post("/api/v1/groups/{id}/finalize", groupId)
				.header("Authorization", "Bearer " + organizerToken))
			.andExpect(status().isOk())
			.andReturn();
		return objectMapper.readTree(finalizeResult.getResponse().getContentAsString());
	}

	@Test
	void memberMarksPaid_organizerConfirms_ledgerEntryAppended() throws Exception {
		String organizerPhone = "+212701000001";
		String memberPhone = "+212701000002";
		String organizerToken = loginAndGetAccessToken(organizerPhone);
		JsonNode group = createAndFinalizeGroup(organizerToken, organizerPhone, memberPhone);
		String groupId = group.get("id").asText();

		String memberToken = loginAndGetAccessToken(memberPhone);
		UUID memberId = extractUserId(memberToken);
		MvcResult listResult = mockMvc.perform(get("/api/v1/groups/{id}/contributions", groupId)
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode contributions = objectMapper.readTree(listResult.getResponse().getContentAsString());
		String memberScheduleId = findScheduleId(contributions, memberId, 1);
		assertThat(memberScheduleId).isNotNull();

		mockMvc.perform(post("/api/v1/groups/{groupId}/contributions/{scheduleId}/mark-paid", groupId, memberScheduleId)
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isOk())
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("MARKED_PAID"));

		mockMvc.perform(post("/api/v1/groups/{groupId}/contributions/{scheduleId}/confirm", groupId, memberScheduleId)
				.header("Authorization", "Bearer " + organizerToken))
			.andExpect(status().isOk())
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("CONFIRMED"));

		List<LedgerEntry> entries = ledgerEntryRepository.findByGroupIdOrderByCreatedAtAsc(UUID.fromString(groupId));
		assertThat(entries).hasSize(1);
		assertThat(entries.get(0).getSource()).isEqualTo(LedgerSource.ORGANIZER_CONFIRMED);
	}

	@Test
	void memberCannotConfirmOwnContribution() throws Exception {
		String organizerPhone = "+212701000003";
		String memberPhone = "+212701000004";
		String organizerToken = loginAndGetAccessToken(organizerPhone);
		JsonNode group = createAndFinalizeGroup(organizerToken, organizerPhone, memberPhone);
		String groupId = group.get("id").asText();
		String memberToken = loginAndGetAccessToken(memberPhone);
		UUID memberId = extractUserId(memberToken);

		JsonNode contributions = objectMapper.readTree(
			mockMvc.perform(get("/api/v1/groups/{id}/contributions", groupId)
					.header("Authorization", "Bearer " + memberToken))
				.andReturn().getResponse().getContentAsString());
		String memberScheduleId = findScheduleId(contributions, memberId, 1);

		mockMvc.perform(post("/api/v1/groups/{groupId}/contributions/{scheduleId}/mark-paid", groupId, memberScheduleId)
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/groups/{groupId}/contributions/{scheduleId}/confirm", groupId, memberScheduleId)
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isForbidden());
	}

	@Test
	void nonMemberCannotListContributions() throws Exception {
		String organizerPhone = "+212701000005";
		String memberPhone = "+212701000006";
		String outsiderPhone = "+212701000007";
		String organizerToken = loginAndGetAccessToken(organizerPhone);
		JsonNode group = createAndFinalizeGroup(organizerToken, organizerPhone, memberPhone);
		String groupId = group.get("id").asText();
		String outsiderToken = loginAndGetAccessToken(outsiderPhone);

		mockMvc.perform(get("/api/v1/groups/{id}/contributions", groupId)
				.header("Authorization", "Bearer " + outsiderToken))
			.andExpect(status().isForbidden());
	}

	@Test
	void ledgerEntries_directUpdateAndDelete_rejectedByDbTrigger() throws Exception {
		String organizerPhone = "+212701000008";
		String memberPhone = "+212701000009";
		String organizerToken = loginAndGetAccessToken(organizerPhone);
		JsonNode group = createAndFinalizeGroup(organizerToken, organizerPhone, memberPhone);
		String groupId = group.get("id").asText();
		String memberToken = loginAndGetAccessToken(memberPhone);
		UUID memberId = extractUserId(memberToken);

		JsonNode contributions = objectMapper.readTree(
			mockMvc.perform(get("/api/v1/groups/{id}/contributions", groupId)
					.header("Authorization", "Bearer " + memberToken))
				.andReturn().getResponse().getContentAsString());
		String memberScheduleId = findScheduleId(contributions, memberId, 1);
		mockMvc.perform(post("/api/v1/groups/{groupId}/contributions/{scheduleId}/mark-paid", groupId, memberScheduleId)
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/groups/{groupId}/contributions/{scheduleId}/confirm", groupId, memberScheduleId)
				.header("Authorization", "Bearer " + organizerToken))
			.andExpect(status().isOk());

		UUID ledgerEntryId = ledgerEntryRepository.findByGroupIdOrderByCreatedAtAsc(UUID.fromString(groupId)).get(0).getId();
		TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

		assertThatThrownBy(() -> transactionTemplate.execute(status -> {
			entityManager.createNativeQuery("UPDATE ledger_entries SET amount = 9999 WHERE id = :id")
				.setParameter("id", ledgerEntryId)
				.executeUpdate();
			return null;
		})).hasMessageContaining("append-only");

		assertThatThrownBy(() -> transactionTemplate.execute(status -> {
			entityManager.createNativeQuery("DELETE FROM ledger_entries WHERE id = :id")
				.setParameter("id", ledgerEntryId)
				.executeUpdate();
			return null;
		})).hasMessageContaining("append-only");
	}

	@Test
	void concurrentConfirmAttempts_onlyOneLedgerEntryAppended() throws Exception {
		String organizerPhone = "+212701000010";
		String memberPhone = "+212701000011";
		String organizerToken = loginAndGetAccessToken(organizerPhone);
		JsonNode group = createAndFinalizeGroup(organizerToken, organizerPhone, memberPhone);
		UUID groupId = UUID.fromString(group.get("id").asText());
		String memberToken = loginAndGetAccessToken(memberPhone);
		UUID memberId = extractUserId(memberToken);

		JsonNode contributions = objectMapper.readTree(
			mockMvc.perform(get("/api/v1/groups/{id}/contributions", groupId)
					.header("Authorization", "Bearer " + memberToken))
				.andReturn().getResponse().getContentAsString());
		UUID memberScheduleId = UUID.fromString(findScheduleId(contributions, memberId, 1));
		mockMvc.perform(post("/api/v1/groups/{groupId}/contributions/{scheduleId}/mark-paid", groupId, memberScheduleId)
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isOk());

		UUID organizerId = extractUserId(organizerToken);

		ExecutorService executor = Executors.newFixedThreadPool(2);
		Callable<Boolean> attempt = () -> {
			try {
				contributionService.confirm(organizerId, groupId, memberScheduleId);
				return true;
			} catch (RuntimeException ex) {
				return false;
			}
		};
		List<Future<Boolean>> futures = executor.invokeAll(List.of(attempt, attempt));
		executor.shutdown();
		executor.awaitTermination(5, TimeUnit.SECONDS);

		long successCount = futures.stream().filter(f -> {
			try {
				return f.get();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}).count();

		assertThat(successCount).isEqualTo(1);
		assertThat(ledgerEntryRepository.findByGroupIdOrderByCreatedAtAsc(groupId)).hasSize(1);
	}

	@Test
	void lateContributionScheduler_flagsOverdueContributionsAsLate() throws Exception {
		String organizerPhone = "+212701000012";
		String memberPhone = "+212701000013";
		String organizerToken = loginAndGetAccessToken(organizerPhone);
		JsonNode group = createAndFinalizeGroup(organizerToken, organizerPhone, memberPhone);
		UUID groupId = UUID.fromString(group.get("id").asText());
		UUID memberId = extractUserId(loginAndGetAccessToken(memberPhone));

		// cycleNumber 99 avoids colliding with the (group_id, cycle_number, user_id)
		// uniqueness constraint on the schedules already generated by finalize.
		ContributionSchedule schedule = new ContributionSchedule(groupId, (short) 99, memberId, LocalDate.now().minusDays(3));
		contributionScheduleRepository.save(schedule);

		lateContributionScheduler.flagLateContributions();

		ContributionSchedule reloaded = contributionScheduleRepository.findById(schedule.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(ContributionStatus.LATE);
	}

	private UUID extractUserId(String accessToken) {
		String payload = accessToken.split("\\.")[1];
		String decoded = new String(java.util.Base64.getUrlDecoder().decode(payload));
		com.fasterxml.jackson.databind.JsonNode node;
		try {
			node = objectMapper.readTree(decoded);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return UUID.fromString(node.get("sub").asText());
	}

	private String findScheduleId(JsonNode contributions, UUID userId, int cycleNumber) {
		for (JsonNode contribution : contributions) {
			if (contribution.get("cycleNumber").asInt() == cycleNumber
					&& contribution.get("userId").asText().equals(userId.toString())) {
				return contribution.get("id").asText();
			}
		}
		throw new IllegalStateException("No schedule found for user " + userId + " cycle " + cycleNumber);
	}

}
