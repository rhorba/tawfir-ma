package ma.tawfir.api.savings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import ma.tawfir.api.TestcontainersConfiguration;
import ma.tawfir.api.auth.OtpChallengeRepository;
import ma.tawfir.api.auth.entity.OtpChallenge;
import ma.tawfir.api.common.PhoneNumberCodec;
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
 * Full-stack: real Postgres (Testcontainers). Covers story 7.1's end-to-end
 * flow — a snapshot is only recorded once the cycle's payout has actually
 * executed (decisions.md 2026-08-03), not merely once every member's cycle-1
 * contribution is CONFIRMED, and GET /users/:id/savings-history is
 * self-or-admin only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SavingsHistoryFlowIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private OtpChallengeRepository otpChallengeRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;
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

	private UUID extractUserId(String accessToken) throws Exception {
		String payload = accessToken.split("\\.")[1];
		String decoded = new String(java.util.Base64.getUrlDecoder().decode(payload));
		return UUID.fromString(objectMapper.readTree(decoded).get("sub").asText());
	}

	private JsonNode createAndFinalizeGroup(String organizerToken, String organizerPhone, String memberPhone) throws Exception {
		String createBody = """
			{"name":"Savings Test","contributionAmount":200,"frequency":"MONTHLY","totalCycles":2,
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

	private void markPaidAndConfirm(String payerToken, String organizerToken, String groupId, String scheduleId) throws Exception {
		mockMvc.perform(post("/api/v1/groups/{groupId}/contributions/{scheduleId}/mark-paid", groupId, scheduleId)
				.header("Authorization", "Bearer " + payerToken))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/groups/{groupId}/contributions/{scheduleId}/confirm", groupId, scheduleId)
				.header("Authorization", "Bearer " + organizerToken))
			.andExpect(status().isOk());
	}

	@Test
	void cycleCompletion_recordsSnapshotForEveryMember_onlyOncePayoutExecutes() throws Exception {
		String organizerPhone = "+212702000001";
		String memberPhone = "+212702000002";
		String organizerToken = loginAndGetAccessToken(organizerPhone);
		JsonNode group = createAndFinalizeGroup(organizerToken, organizerPhone, memberPhone);
		String groupId = group.get("id").asText();

		String memberToken = loginAndGetAccessToken(memberPhone);
		UUID organizerId = extractUserId(organizerToken);
		UUID memberId = extractUserId(memberToken);

		JsonNode contributions = objectMapper.readTree(
			mockMvc.perform(get("/api/v1/groups/{id}/contributions", groupId)
					.header("Authorization", "Bearer " + organizerToken))
				.andReturn().getResponse().getContentAsString());
		String organizerScheduleId = findScheduleId(contributions, organizerId, 1);
		String memberScheduleId = findScheduleId(contributions, memberId, 1);

		// First of two cycle-1 contributions confirmed — payout can't execute yet, no snapshot.
		markPaidAndConfirm(organizerToken, organizerToken, groupId, organizerScheduleId);
		mockMvc.perform(get("/api/v1/users/{userId}/savings-history", organizerId)
				.header("Authorization", "Bearer " + organizerToken))
			.andExpect(status().isOk())
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$").isEmpty());

		// Second (last) cycle-1 contribution confirmed — all contributions in, but the payout
		// itself hasn't executed yet, so still no snapshot (story 7.1 tracks payout completion).
		markPaidAndConfirm(memberToken, organizerToken, groupId, memberScheduleId);
		mockMvc.perform(get("/api/v1/users/{userId}/savings-history", organizerId)
				.header("Authorization", "Bearer " + organizerToken))
			.andExpect(status().isOk())
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$").isEmpty());

		// Organizer executes the cycle-1 payout — the cycle is now genuinely complete, snapshot recorded for both members.
		String payoutId = findPayoutId(groupId, organizerToken, 1);
		mockMvc.perform(post("/api/v1/groups/{groupId}/payouts/{payoutId}/execute", groupId, payoutId)
				.header("Authorization", "Bearer " + organizerToken))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/users/{userId}/savings-history", organizerId)
				.header("Authorization", "Bearer " + organizerToken))
			.andExpect(status().isOk())
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].cyclesCompleted").value(1))
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].onTimeRate").value(100.0))
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].disputesInvolved").value(0));

		mockMvc.perform(get("/api/v1/users/{userId}/savings-history", memberId)
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isOk())
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].cyclesCompleted").value(1));
	}

	@Test
	void savingsHistory_otherUser_returns403() throws Exception {
		String userPhone = "+212702000003";
		String otherPhone = "+212702000004";
		UUID userId = extractUserId(loginAndGetAccessToken(userPhone));
		String otherToken = loginAndGetAccessToken(otherPhone);

		mockMvc.perform(get("/api/v1/users/{userId}/savings-history", userId)
				.header("Authorization", "Bearer " + otherToken))
			.andExpect(status().isForbidden());
	}

}
