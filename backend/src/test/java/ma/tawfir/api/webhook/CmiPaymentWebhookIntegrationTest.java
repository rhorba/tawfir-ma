package ma.tawfir.api.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import ma.tawfir.api.TestcontainersConfiguration;
import ma.tawfir.api.auth.OtpChallengeRepository;
import ma.tawfir.api.auth.entity.OtpChallenge;
import ma.tawfir.api.common.PhoneNumberCodec;
import ma.tawfir.api.group.ContributionScheduleRepository;
import ma.tawfir.api.ledger.LedgerEntryRepository;
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
 * Full-stack: real Postgres (Testcontainers). Covers story 3.3's Gherkin
 * scenarios end-to-end: a validly-signed webhook auto-confirms a contribution
 * (no manual mark-paid/organizer-confirm needed) and an invalid signature is
 * rejected with no ledger entry created.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CmiPaymentWebhookIntegrationTest {

	// Must match application.yml's default (CMI_WEBHOOK_SECRET unset in this test env).
	private static final String WEBHOOK_SECRET = "mock-webhook-secret";

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
			{"name":"Webhook Test","contributionAmount":200,"frequency":"MONTHLY","totalCycles":2,
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

	private String findScheduleId(JsonNode contributions, UUID userId, int cycleNumber) {
		for (JsonNode contribution : contributions) {
			if (contribution.get("cycleNumber").asInt() == cycleNumber
					&& contribution.get("userId").asText().equals(userId.toString())) {
				return contribution.get("id").asText();
			}
		}
		throw new IllegalStateException("No schedule found for user " + userId + " cycle " + cycleNumber);
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
	void validlySignedWebhook_autoConfirmsPendingContribution_noManualActionNeeded() throws Exception {
		String organizerPhone = "+212705000001";
		String memberPhone = "+212705000002";
		String organizerToken = loginAndGetAccessToken(organizerPhone);
		JsonNode group = createAndFinalizeGroup(organizerToken, organizerPhone, memberPhone);
		String groupId = group.get("id").asText();
		UUID memberId = extractUserId(loginAndGetAccessToken(memberPhone));

		JsonNode contributions = objectMapper.readTree(
			mockMvc.perform(get("/api/v1/groups/{id}/contributions", groupId)
					.header("Authorization", "Bearer " + organizerToken))
				.andReturn().getResponse().getContentAsString());
		String memberScheduleId = findScheduleId(contributions, memberId, 1);

		String body = "{\"contributionScheduleId\":\"" + memberScheduleId + "\",\"amount\":200,\"providerReference\":\"MOCK-1\"}";
		mockMvc.perform(post("/api/v1/webhooks/cmi/payment-confirmation")
				.header("X-Cmi-Signature", sign(body))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isOk());

		assertThat(contributionScheduleRepository.findById(UUID.fromString(memberScheduleId)).orElseThrow().getStatus().name())
			.isEqualTo("CONFIRMED");
		assertThat(ledgerEntryRepository.findByGroupIdOrderByCreatedAtAsc(UUID.fromString(groupId))).hasSize(1);

		// Replay: idempotent, no second ledger entry.
		mockMvc.perform(post("/api/v1/webhooks/cmi/payment-confirmation")
				.header("X-Cmi-Signature", sign(body))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isOk());
		assertThat(ledgerEntryRepository.findByGroupIdOrderByCreatedAtAsc(UUID.fromString(groupId))).hasSize(1);
	}

	@Test
	void invalidSignature_rejected_noLedgerEntryCreated() throws Exception {
		String organizerPhone = "+212705000003";
		String memberPhone = "+212705000004";
		String organizerToken = loginAndGetAccessToken(organizerPhone);
		JsonNode group = createAndFinalizeGroup(organizerToken, organizerPhone, memberPhone);
		String groupId = group.get("id").asText();
		UUID memberId = extractUserId(loginAndGetAccessToken(memberPhone));

		JsonNode contributions = objectMapper.readTree(
			mockMvc.perform(get("/api/v1/groups/{id}/contributions", groupId)
					.header("Authorization", "Bearer " + organizerToken))
				.andReturn().getResponse().getContentAsString());
		String memberScheduleId = findScheduleId(contributions, memberId, 1);

		String body = "{\"contributionScheduleId\":\"" + memberScheduleId + "\",\"amount\":200,\"providerReference\":\"MOCK-1\"}";
		mockMvc.perform(post("/api/v1/webhooks/cmi/payment-confirmation")
				.header("X-Cmi-Signature", "totally-wrong-signature")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isUnauthorized());

		assertThat(contributionScheduleRepository.findById(UUID.fromString(memberScheduleId)).orElseThrow().getStatus().name())
			.isNotEqualTo("CONFIRMED");
		assertThat(ledgerEntryRepository.findByGroupIdOrderByCreatedAtAsc(UUID.fromString(groupId))).isEmpty();
	}

}
