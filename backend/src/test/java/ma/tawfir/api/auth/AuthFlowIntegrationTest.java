package ma.tawfir.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import ma.tawfir.api.TestcontainersConfiguration;
import ma.tawfir.api.auth.entity.OtpChallenge;
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
 * Full-stack: real Postgres (Testcontainers), real Flyway migrations, real
 * SecurityConfig/JwtAuthenticationFilter. Covers the adversarial-checklist
 * items in test-strategy-tawfir.md §4 (auth abuse) end-to-end rather than
 * through mocks.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthFlowIntegrationTest {

	private static final String PHONE = "+212600000001";
	private static final String CODE = "654321";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private OtpChallengeRepository otpChallengeRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void cleanOtpChallenges() {
		otpChallengeRepository.deleteAll();
	}

	@Test
	void otpRequest_rateLimited_after5RequestsIn10Minutes() throws Exception {
		String body = "{\"phoneNumber\":\"+212611111111\"}";
		for (int i = 0; i < 5; i++) {
			mockMvc.perform(post("/api/v1/auth/otp/request")
					.contentType(MediaType.APPLICATION_JSON)
					.content(body))
				.andExpect(status().isAccepted());
		}

		mockMvc.perform(post("/api/v1/auth/otp/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isTooManyRequests());
	}

	@Test
	void verifyOtp_expiredChallenge_rejectedWithoutIssuingJwt() throws Exception {
		otpChallengeRepository.save(new OtpChallenge(PHONE, passwordEncoder.encode(CODE), Instant.now().minusSeconds(1)));

		mockMvc.perform(post("/api/v1/auth/otp/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"phoneNumber\":\"" + PHONE + "\",\"code\":\"" + CODE + "\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void fullAuthFlow_verifyThenRotateThenDetectReuseThenLogout() throws Exception {
		otpChallengeRepository.save(new OtpChallenge(PHONE, passwordEncoder.encode(CODE), Instant.now().plusSeconds(300)));

		MvcResult verifyResult = mockMvc.perform(post("/api/v1/auth/otp/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"phoneNumber\":\"" + PHONE + "\",\"code\":\"" + CODE + "\"}"))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode firstTokens = objectMapper.readTree(verifyResult.getResponse().getContentAsString());
		String firstRefreshToken = firstTokens.get("refreshToken").asText();
		assertThat(firstTokens.get("accessToken").asText()).isNotBlank();

		MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"" + firstRefreshToken + "\"}"))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode secondTokens = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
		String secondRefreshToken = secondTokens.get("refreshToken").asText();

		// Reuse of the already-rotated first token must be rejected AND revoke the whole family.
		mockMvc.perform(post("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"" + firstRefreshToken + "\"}"))
			.andExpect(status().isUnauthorized());

		// The second-generation token, though never reused itself, is now revoked too.
		mockMvc.perform(post("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"" + secondRefreshToken + "\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void logout_thenRefreshWithSameToken_isRejected() throws Exception {
		otpChallengeRepository.save(new OtpChallenge(PHONE, passwordEncoder.encode(CODE), Instant.now().plusSeconds(300)));

		MvcResult verifyResult = mockMvc.perform(post("/api/v1/auth/otp/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"phoneNumber\":\"" + PHONE + "\",\"code\":\"" + CODE + "\"}"))
			.andExpect(status().isOk())
			.andReturn();
		String refreshToken = objectMapper.readTree(verifyResult.getResponse().getContentAsString())
			.get("refreshToken").asText();

		mockMvc.perform(post("/api/v1/auth/logout")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"" + refreshToken + "\"}"))
			.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"" + refreshToken + "\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void protectedEndpoint_withoutToken_isRejected() throws Exception {
		mockMvc.perform(post("/api/v1/groups"))
			.andExpect(status().isUnauthorized());
	}

}
