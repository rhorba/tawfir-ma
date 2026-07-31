package ma.tawfir.api.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import ma.tawfir.api.TestcontainersConfiguration;
import ma.tawfir.api.auth.OtpChallengeRepository;
import ma.tawfir.api.auth.entity.OtpChallenge;
import ma.tawfir.api.group.entity.PayoutStatus;
import ma.tawfir.api.ledger.LedgerEntryRepository;
import ma.tawfir.api.ledger.entity.LedgerEntry;
import ma.tawfir.api.ledger.entity.LedgerEntryType;
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
 * Full-stack: real Postgres (Testcontainers). Covers Sprint 4 Batch 2's three
 * payout entry points end-to-end: organizer manual override (4.2), the CMI
 * payout-confirmation webhook (4.3), and the auto-execution cron (4.1, which
 * needs its due-date check bypassed here since a freshly-finalized group's
 * first payout is always scheduled in the future — see backdateToToday()).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PayoutLifecycleIntegrationTest {

	// Must match application.yml's default (CMI_WEBHOOK_SECRET unset in this test env).
	private static final String WEBHOOK_SECRET = "mock-webhook-secret";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private OtpChallengeRepository otpChallengeRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private PayoutScheduleRepository payoutScheduleRepository;
	@Autowired
	private LedgerEntryRepository ledgerEntryRepository;
	@Autowired
	private PayoutScheduler payoutScheduler;
	@Autowired
	private EntityManager entityManager;
	@Autowired
	private PlatformTransactionManager transactionManager;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void cleanOtpChallenges() {
		otpChallengeRepository.deleteAll();
	}

	private String loginAndGetAccessToken(String phoneNumber) throws Exception {
		String code = "654321";
		otpChallengeRepository.save(new OtpChallenge(phoneNumber, passwordEncoder.encode(code), Instant.now().plusSeconds(300)));
		MvcResult result = mockMvc.perform(post("/api/v1/auth/otp/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"phoneNumber\":\"" + phoneNumber + "\",\"code\":\"" + code + "\"}"))
			.andExpect(status().isOk())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
	}

	private JsonNode createAndFinalizeGroup(String organizerToken, String organizerPhone, String memberPhone) throws Exception {
		String createBody = """
			{"name":"Payout Test","contributionAmount":300,"frequency":"MONTHLY","totalCycles":2,
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

	/** Marks-paid and confirms every cycle-1 contribution so the cycle-1 payout becomes eligible. */
	private void confirmAllCycleOneContributions(String groupId, String organizerToken, String memberToken) throws Exception {
		UUID organizerId = extractUserId(organizerToken);
		UUID memberId = extractUserId(memberToken);
		JsonNode contributions = objectMapper.readTree(
			mockMvc.perform(get("/api/v1/groups/{id}/contributions", groupId)
					.header("Authorization", "Bearer " + organizerToken))
				.andReturn().getResponse().getContentAsString());

		markPaidAndConfirm(groupId, findScheduleId(contributions, organizerId, 1), organizerToken, organizerToken);
		markPaidAndConfirm(groupId, findScheduleId(contributions, memberId, 1), memberToken, organizerToken);
	}

	private void markPaidAndConfirm(String groupId, String scheduleId, String ownerToken, String organizerToken) throws Exception {
		mockMvc.perform(post("/api/v1/groups/{groupId}/contributions/{scheduleId}/mark-paid", groupId, scheduleId)
				.header("Authorization", "Bearer " + ownerToken))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/groups/{groupId}/contributions/{scheduleId}/confirm", groupId, scheduleId)
				.header("Authorization", "Bearer " + organizerToken))
			.andExpect(status().isOk());
	}

	private String findScheduleId(JsonNode schedules, UUID userId, int cycleNumber) {
		for (JsonNode schedule : schedules) {
			if (schedule.get("cycleNumber").asInt() == cycleNumber && schedule.get("userId").asText().equals(userId.toString())) {
				return schedule.get("id").asText();
			}
		}
		throw new IllegalStateException("No schedule found for user " + userId + " cycle " + cycleNumber);
	}

	/** Confirming contributions also appends CONTRIBUTION-type entries to the same group's ledger, so payout assertions filter to PAYOUT only. */
	private List<LedgerEntry> payoutLedgerEntries(String groupId) {
		return ledgerEntryRepository.findByGroupIdOrderByCreatedAtAsc(UUID.fromString(groupId)).stream()
			.filter(entry -> entry.getEntryType() == LedgerEntryType.PAYOUT)
			.toList();
	}

	private String findPayoutId(String groupId, String token, int cycleNumber) throws Exception {
		JsonNode payouts = objectMapper.readTree(
			mockMvc.perform(get("/api/v1/groups/{id}/payouts", groupId)
					.header("Authorization", "Bearer " + token))
				.andReturn().getResponse().getContentAsString());
		for (JsonNode payout : payouts) {
			if (payout.get("cycleNumber").asInt() == cycleNumber) {
				return payout.get("id").asText();
			}
		}
		throw new IllegalStateException("No payout found for cycle " + cycleNumber);
	}

	/** A freshly-finalized cycle-1 payout is always scheduled in the future; the cron test backdates it. */
	private void backdateToToday(UUID payoutId) {
		new TransactionTemplate(transactionManager).executeWithoutResult(status ->
			entityManager.createNativeQuery("UPDATE payout_schedules SET scheduled_date = :today WHERE id = :id")
				.setParameter("today", LocalDate.now())
				.setParameter("id", payoutId)
				.executeUpdate());
	}

	private UUID extractUserId(String accessToken) throws Exception {
		String payload = accessToken.split("\\.")[1];
		String decoded = new String(java.util.Base64.getUrlDecoder().decode(payload));
		return UUID.fromString(objectMapper.readTree(decoded).get("sub").asText());
	}

	private static String sign(String body) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
		StringBuilder hex = new StringBuilder();
		for (byte b : hash) {
			hex.append(String.format("%02x", b));
		}
		return hex.toString();
	}

	@Test
	void organizerManualOverride_executesPayoutAndAppendsLedgerEntry() throws Exception {
		String organizerPhone = "+212706000001";
		String memberPhone = "+212706000002";
		String organizerToken = loginAndGetAccessToken(organizerPhone);
		String memberToken = loginAndGetAccessToken(memberPhone);
		JsonNode group = createAndFinalizeGroup(organizerToken, organizerPhone, memberPhone);
		String groupId = group.get("id").asText();
		confirmAllCycleOneContributions(groupId, organizerToken, memberToken);
		String payoutId = findPayoutId(groupId, organizerToken, 1);

		mockMvc.perform(post("/api/v1/groups/{groupId}/payouts/{payoutId}/execute", groupId, payoutId)
				.header("Authorization", "Bearer " + organizerToken))
			.andExpect(status().isOk())
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("MANUAL_OVERRIDE"));

		List<LedgerEntry> entries = payoutLedgerEntries(groupId);
		assertThat(entries).hasSize(1);
		assertThat(entries.get(0).getSource()).isEqualTo(LedgerSource.ORGANIZER_CONFIRMED);
	}

	@Test
	void nonOrganizerManualOverride_forbidden() throws Exception {
		String organizerPhone = "+212706000003";
		String memberPhone = "+212706000004";
		String organizerToken = loginAndGetAccessToken(organizerPhone);
		String memberToken = loginAndGetAccessToken(memberPhone);
		JsonNode group = createAndFinalizeGroup(organizerToken, organizerPhone, memberPhone);
		String groupId = group.get("id").asText();
		confirmAllCycleOneContributions(groupId, organizerToken, memberToken);
		String payoutId = findPayoutId(groupId, organizerToken, 1);

		mockMvc.perform(post("/api/v1/groups/{groupId}/payouts/{payoutId}/execute", groupId, payoutId)
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isForbidden());

		assertThat(payoutLedgerEntries(groupId)).isEmpty();
	}

	@Test
	void validlySignedWebhook_drivesPendingPayoutToExecuted_andIsIdempotentOnReplay() throws Exception {
		String organizerPhone = "+212706000005";
		String memberPhone = "+212706000006";
		String organizerToken = loginAndGetAccessToken(organizerPhone);
		String memberToken = loginAndGetAccessToken(memberPhone);
		JsonNode group = createAndFinalizeGroup(organizerToken, organizerPhone, memberPhone);
		String groupId = group.get("id").asText();
		confirmAllCycleOneContributions(groupId, organizerToken, memberToken);
		String payoutId = findPayoutId(groupId, organizerToken, 1);

		String body = "{\"payoutScheduleId\":\"" + payoutId + "\",\"amount\":600,\"providerReference\":\"MOCK-1\"}";
		mockMvc.perform(post("/api/v1/webhooks/cmi/payout-confirmation")
				.header("X-Cmi-Signature", sign(body))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isOk());

		assertThat(payoutScheduleRepository.findById(UUID.fromString(payoutId)).orElseThrow().getStatus())
			.isEqualTo(PayoutStatus.EXECUTED);
		List<LedgerEntry> entries = payoutLedgerEntries(groupId);
		assertThat(entries).hasSize(1);
		assertThat(entries.get(0).getSource()).isEqualTo(LedgerSource.CMI_WEBHOOK);

		// Replay: idempotent, no second ledger entry.
		mockMvc.perform(post("/api/v1/webhooks/cmi/payout-confirmation")
				.header("X-Cmi-Signature", sign(body))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isOk());
		assertThat(payoutLedgerEntries(groupId)).hasSize(1);
	}

	@Test
	void invalidSignatureOnPayoutWebhook_rejected_noStateChange() throws Exception {
		String organizerPhone = "+212706000007";
		String memberPhone = "+212706000008";
		String organizerToken = loginAndGetAccessToken(organizerPhone);
		String memberToken = loginAndGetAccessToken(memberPhone);
		JsonNode group = createAndFinalizeGroup(organizerToken, organizerPhone, memberPhone);
		String groupId = group.get("id").asText();
		confirmAllCycleOneContributions(groupId, organizerToken, memberToken);
		String payoutId = findPayoutId(groupId, organizerToken, 1);

		String body = "{\"payoutScheduleId\":\"" + payoutId + "\",\"amount\":600,\"providerReference\":\"MOCK-1\"}";
		mockMvc.perform(post("/api/v1/webhooks/cmi/payout-confirmation")
				.header("X-Cmi-Signature", "totally-wrong-signature")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isUnauthorized());

		assertThat(payoutScheduleRepository.findById(UUID.fromString(payoutId)).orElseThrow().getStatus())
			.isEqualTo(PayoutStatus.PENDING);
		assertThat(payoutLedgerEntries(groupId)).isEmpty();
	}

	@Test
	void payoutScheduler_allContributionsConfirmedAndDue_autoExecutes() throws Exception {
		String organizerPhone = "+212706000009";
		String memberPhone = "+212706000010";
		String organizerToken = loginAndGetAccessToken(organizerPhone);
		String memberToken = loginAndGetAccessToken(memberPhone);
		JsonNode group = createAndFinalizeGroup(organizerToken, organizerPhone, memberPhone);
		String groupId = group.get("id").asText();
		confirmAllCycleOneContributions(groupId, organizerToken, memberToken);
		UUID payoutId = UUID.fromString(findPayoutId(groupId, organizerToken, 1));
		backdateToToday(payoutId);

		payoutScheduler.executeDuePayouts();

		assertThat(payoutScheduleRepository.findById(payoutId).orElseThrow().getStatus()).isEqualTo(PayoutStatus.EXECUTED);
		List<LedgerEntry> entries = payoutLedgerEntries(groupId);
		assertThat(entries).hasSize(1);
		assertThat(entries.get(0).getSource()).isEqualTo(LedgerSource.SYSTEM_SCHEDULED);
	}

	@Test
	void payoutScheduler_dueButContributionsNotAllConfirmed_staysPending() throws Exception {
		String organizerPhone = "+212706000011";
		String memberPhone = "+212706000012";
		String organizerToken = loginAndGetAccessToken(organizerPhone);
		String memberToken = loginAndGetAccessToken(memberPhone);
		JsonNode group = createAndFinalizeGroup(organizerToken, organizerPhone, memberPhone);
		String groupId = group.get("id").asText();
		// Only the organizer's own cycle-1 contribution is confirmed; the member's is left PENDING.
		UUID organizerId = extractUserId(organizerToken);
		JsonNode contributions = objectMapper.readTree(
			mockMvc.perform(get("/api/v1/groups/{id}/contributions", groupId)
					.header("Authorization", "Bearer " + organizerToken))
				.andReturn().getResponse().getContentAsString());
		markPaidAndConfirm(groupId, findScheduleId(contributions, organizerId, 1), organizerToken, organizerToken);
		UUID payoutId = UUID.fromString(findPayoutId(groupId, organizerToken, 1));
		backdateToToday(payoutId);

		payoutScheduler.executeDuePayouts();

		assertThat(payoutScheduleRepository.findById(payoutId).orElseThrow().getStatus()).isEqualTo(PayoutStatus.PENDING);
		assertThat(payoutLedgerEntries(groupId)).isEmpty();
	}

}
