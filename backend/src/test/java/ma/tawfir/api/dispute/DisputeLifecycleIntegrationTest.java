package ma.tawfir.api.dispute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
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
import ma.tawfir.api.dispute.dto.ResolveDisputeRequest;
import ma.tawfir.api.dispute.entity.Dispute;
import ma.tawfir.api.dispute.entity.DisputeStatus;
import ma.tawfir.api.ledger.LedgerEntryRepository;
import ma.tawfir.api.ledger.entity.LedgerEntry;
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

/**
 * Full-stack: real Postgres (Testcontainers) — covers stories 5.1/5.2's
 * Gherkin scenarios end-to-end plus the test-strategy-tawfir.md §4 race
 * condition item for concurrent dispute resolution.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DisputeLifecycleIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private OtpChallengeRepository otpChallengeRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private LedgerEntryRepository ledgerEntryRepository;
	@Autowired
	private DisputeRepository disputeRepository;
	@Autowired
	private DisputeService disputeService;
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
			new OtpChallenge(phoneNumberCodec.hash(phoneNumber), "127.0.0.1", passwordEncoder.encode(code), Instant.now().plusSeconds(300)));

		MvcResult result = mockMvc.perform(post("/api/v1/auth/otp/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"phoneNumber\":\"" + phoneNumber + "\",\"code\":\"" + code + "\"}"))
			.andExpect(status().isOk())
			.andReturn();

		return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
	}

	private JsonNode createAndFinalizeGroup(String organizerToken, String organizerPhone, String memberPhone) throws Exception {
		String createBody = """
			{"name":"Dispute Test","contributionAmount":200,"frequency":"MONTHLY","totalCycles":2,
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

	private UUID extractUserId(String accessToken) throws Exception {
		String payload = accessToken.split("\\.")[1];
		String decoded = new String(java.util.Base64.getUrlDecoder().decode(payload));
		return UUID.fromString(objectMapper.readTree(decoded).get("sub").asText());
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

	/** Marks the member's cycle-1 contribution paid and organizer-confirms it, returning the resulting ledger entry id. */
	private UUID confirmMemberContribution(String organizerToken, String memberToken, UUID groupId, UUID memberId) throws Exception {
		JsonNode contributions = objectMapper.readTree(
			mockMvc.perform(get("/api/v1/groups/{id}/contributions", groupId)
					.header("Authorization", "Bearer " + memberToken))
				.andReturn().getResponse().getContentAsString());
		String scheduleId = findScheduleId(contributions, memberId, 1);

		mockMvc.perform(post("/api/v1/groups/{groupId}/contributions/{scheduleId}/mark-paid", groupId, scheduleId)
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/groups/{groupId}/contributions/{scheduleId}/confirm", groupId, scheduleId)
				.header("Authorization", "Bearer " + organizerToken))
			.andExpect(status().isOk());

		List<LedgerEntry> entries = ledgerEntryRepository.findByGroupIdOrderByCreatedAtAsc(groupId);
		return entries.get(entries.size() - 1).getId();
	}

	@Test
	void memberOpensDispute_onConfirmedContribution_disputeCreatedOpen() throws Exception {
		String organizerPhone = "+212702000001";
		String memberPhone = "+212702000002";
		String organizerToken = loginAndGetAccessToken(organizerPhone);
		JsonNode group = createAndFinalizeGroup(organizerToken, organizerPhone, memberPhone);
		UUID groupId = UUID.fromString(group.get("id").asText());
		String memberToken = loginAndGetAccessToken(memberPhone);
		UUID memberId = extractUserId(memberToken);
		UUID ledgerEntryId = confirmMemberContribution(organizerToken, memberToken, groupId, memberId);

		mockMvc.perform(post("/api/v1/groups/{groupId}/disputes", groupId)
				.header("Authorization", "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"ledgerEntryId\":\"" + ledgerEntryId + "\",\"reason\":\"amount looks wrong\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("OPEN"));
	}

	@Test
	void nonMemberCannotOpenDispute() throws Exception {
		String organizerPhone = "+212702000003";
		String memberPhone = "+212702000004";
		String outsiderPhone = "+212702000005";
		String organizerToken = loginAndGetAccessToken(organizerPhone);
		JsonNode group = createAndFinalizeGroup(organizerToken, organizerPhone, memberPhone);
		UUID groupId = UUID.fromString(group.get("id").asText());
		String memberToken = loginAndGetAccessToken(memberPhone);
		UUID memberId = extractUserId(memberToken);
		UUID ledgerEntryId = confirmMemberContribution(organizerToken, memberToken, groupId, memberId);
		String outsiderToken = loginAndGetAccessToken(outsiderPhone);

		mockMvc.perform(post("/api/v1/groups/{groupId}/disputes", groupId)
				.header("Authorization", "Bearer " + outsiderToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"ledgerEntryId\":\"" + ledgerEntryId + "\",\"reason\":\"amount looks wrong\"}"))
			.andExpect(status().isForbidden());
	}

	@Test
	void organizerResolvesDispute_accepted_disputeUpdated() throws Exception {
		String organizerPhone = "+212702000006";
		String memberPhone = "+212702000007";
		String organizerToken = loginAndGetAccessToken(organizerPhone);
		JsonNode group = createAndFinalizeGroup(organizerToken, organizerPhone, memberPhone);
		UUID groupId = UUID.fromString(group.get("id").asText());
		String memberToken = loginAndGetAccessToken(memberPhone);
		UUID memberId = extractUserId(memberToken);
		UUID ledgerEntryId = confirmMemberContribution(organizerToken, memberToken, groupId, memberId);

		MvcResult openResult = mockMvc.perform(post("/api/v1/groups/{groupId}/disputes", groupId)
				.header("Authorization", "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"ledgerEntryId\":\"" + ledgerEntryId + "\",\"reason\":\"amount looks wrong\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		String disputeId = objectMapper.readTree(openResult.getResponse().getContentAsString()).get("id").asText();

		mockMvc.perform(patch("/api/v1/disputes/{disputeId}/resolve", disputeId)
				.header("Authorization", "Bearer " + organizerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"resolution\":\"ACCEPTED\",\"resolutionReason\":\"confirmed the amount was wrong\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ACCEPTED"))
			.andExpect(jsonPath("$.resolutionReason").value("confirmed the amount was wrong"));
	}

	@Test
	void nonOrganizerMemberCannotResolveDispute() throws Exception {
		String organizerPhone = "+212702000008";
		String memberPhone = "+212702000009";
		String organizerToken = loginAndGetAccessToken(organizerPhone);
		JsonNode group = createAndFinalizeGroup(organizerToken, organizerPhone, memberPhone);
		UUID groupId = UUID.fromString(group.get("id").asText());
		String memberToken = loginAndGetAccessToken(memberPhone);
		UUID memberId = extractUserId(memberToken);
		UUID ledgerEntryId = confirmMemberContribution(organizerToken, memberToken, groupId, memberId);

		MvcResult openResult = mockMvc.perform(post("/api/v1/groups/{groupId}/disputes", groupId)
				.header("Authorization", "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"ledgerEntryId\":\"" + ledgerEntryId + "\",\"reason\":\"amount looks wrong\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		String disputeId = objectMapper.readTree(openResult.getResponse().getContentAsString()).get("id").asText();

		mockMvc.perform(patch("/api/v1/disputes/{disputeId}/resolve", disputeId)
				.header("Authorization", "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"resolution\":\"ACCEPTED\",\"resolutionReason\":\"self-service accept\"}"))
			.andExpect(status().isForbidden());
	}

	@Test
	void concurrentResolveAttempts_onlyOneSucceeds() throws Exception {
		String organizerPhone = "+212702000010";
		String memberPhone = "+212702000011";
		String organizerToken = loginAndGetAccessToken(organizerPhone);
		JsonNode group = createAndFinalizeGroup(organizerToken, organizerPhone, memberPhone);
		UUID groupId = UUID.fromString(group.get("id").asText());
		String memberToken = loginAndGetAccessToken(memberPhone);
		UUID memberId = extractUserId(memberToken);
		UUID ledgerEntryId = confirmMemberContribution(organizerToken, memberToken, groupId, memberId);
		UUID organizerId = extractUserId(organizerToken);

		MvcResult openResult = mockMvc.perform(post("/api/v1/groups/{groupId}/disputes", groupId)
				.header("Authorization", "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"ledgerEntryId\":\"" + ledgerEntryId + "\",\"reason\":\"amount looks wrong\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		UUID disputeId = UUID.fromString(objectMapper.readTree(openResult.getResponse().getContentAsString()).get("id").asText());

		ExecutorService executor = Executors.newFixedThreadPool(2);
		Callable<Boolean> attempt = () -> {
			try {
				disputeService.resolveDispute(
					organizerId, disputeId, new ResolveDisputeRequest(DisputeStatus.ACCEPTED, "race"), false);
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
		Dispute finalState = disputeRepository.findById(disputeId).orElseThrow();
		assertThat(finalState.getStatus()).isEqualTo(DisputeStatus.ACCEPTED);
	}

}
