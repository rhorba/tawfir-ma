package ma.tawfir.api.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import ma.tawfir.api.TestcontainersConfiguration;
import ma.tawfir.api.auth.OtpChallengeRepository;
import ma.tawfir.api.auth.entity.OtpChallenge;
import ma.tawfir.api.user.UserRepository;
import ma.tawfir.api.user.entity.Role;
import ma.tawfir.api.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Full-stack: real Postgres (Testcontainers), real Flyway migrations, real
 * SecurityConfig/JwtAuthenticationFilter/GlobalExceptionHandler. Covers story
 * 1.4's end-to-end flow: admin-only setup/verify, the OTP-login branch for
 * MFA-enabled admins, the mfa-pending token's rejection everywhere except
 * /auth/mfa/verify, and the regression guarantee that everyone else's login
 * is byte-for-byte unaffected.
 *
 * <p>There is no admin-provisioning endpoint yet (out of scope for this batch),
 * so tests promote a freshly auto-created user to ADMIN directly via the
 * repository/reflection, the same test-only mechanism AuthServiceTest already
 * uses for entity fields with no public setter.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MfaFlowIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private OtpChallengeRepository otpChallengeRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private TotpGenerator totpGenerator;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void cleanOtpChallenges() {
		otpChallengeRepository.deleteAll();
	}

	private String loginAndGetBody(String phoneNumber) throws Exception {
		String code = "654321";
		otpChallengeRepository.save(new OtpChallenge(phoneNumber, passwordEncoder.encode(code), Instant.now().plusSeconds(300)));

		MvcResult result = mockMvc.perform(post("/api/v1/auth/otp/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"phoneNumber\":\"" + phoneNumber + "\",\"code\":\"" + code + "\"}"))
			.andExpect(status().isOk())
			.andReturn();
		return result.getResponse().getContentAsString();
	}

	private String promoteToAdminAndGetToken(String phoneNumber) throws Exception {
		String firstLoginBody = loginAndGetBody(phoneNumber);
		User user = userRepository.findByPhoneNumber(phoneNumber).orElseThrow();
		ReflectionTestUtils.setField(user, "role", Role.ADMIN);
		userRepository.save(user);

		// Re-login: the first token above still carries the pre-promotion MEMBER role claim
		// (JWTs are stateless), so a fresh admin-scoped token is needed for admin endpoints.
		return objectMapper.readTree(loginAndGetBody(phoneNumber)).get("accessToken").asText();
	}

	@Test
	void setup_nonAdmin_returns403() throws Exception {
		String memberToken = objectMapper.readTree(loginAndGetBody("+212703000001")).get("accessToken").asText();

		mockMvc.perform(post("/api/v1/admin/mfa/setup")
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isForbidden());
	}

	@Test
	void verify_beforeSetup_returns400() throws Exception {
		String adminToken = promoteToAdminAndGetToken("+212703000002");

		mockMvc.perform(post("/api/v1/admin/mfa/verify")
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"123456\"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void setupThenVerify_wrongCodeRejected_correctCodeActivates() throws Exception {
		String adminToken = promoteToAdminAndGetToken("+212703000003");

		MvcResult setupResult = mockMvc.perform(post("/api/v1/admin/mfa/setup")
				.header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode setupBody = objectMapper.readTree(setupResult.getResponse().getContentAsString());
		String secret = setupBody.get("secret").asText();
		assertThat(setupBody.get("otpauthUri").asText()).startsWith("otpauth://totp/");

		mockMvc.perform(post("/api/v1/admin/mfa/verify")
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"000000\"}"))
			.andExpect(status().isUnauthorized());

		String correctCode = totpGenerator.generate(secret, Instant.now());
		mockMvc.perform(post("/api/v1/admin/mfa/verify")
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"" + correctCode + "\"}"))
			.andExpect(status().isNoContent());

		User user = userRepository.findByPhoneNumber("+212703000003").orElseThrow();
		assertThat(user.getTotpEnabledAt()).isNotNull();
	}

	@Test
	void fullMfaLoginFlow_pendingTokenRejectedElsewhere_correctCodeIssuesRealTokens() throws Exception {
		String phoneNumber = "+212703000004";
		String adminToken = promoteToAdminAndGetToken(phoneNumber);
		String secret = activateMfa(adminToken, phoneNumber);

		// Login again now that MFA is enabled: must get an mfa-pending response, not real tokens.
		String code = "654321";
		otpChallengeRepository.save(
			new OtpChallenge(phoneNumber, passwordEncoder.encode(code), Instant.now().plusSeconds(300)));
		MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/otp/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"phoneNumber\":\"" + phoneNumber + "\",\"code\":\"" + code + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mfaRequired").value(true))
			.andExpect(jsonPath("$.accessToken").doesNotExist())
			.andReturn();
		String pendingToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
			.get("mfaPendingToken").asText();

		// The pending token must not work as a normal bearer token anywhere else.
		mockMvc.perform(get("/api/v1/groups").header("Authorization", "Bearer " + pendingToken))
			.andExpect(status().isUnauthorized());

		// Wrong TOTP code at the login-completion endpoint is rejected.
		mockMvc.perform(post("/api/v1/auth/mfa/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"mfaPendingToken\":\"" + pendingToken + "\",\"code\":\"000000\"}"))
			.andExpect(status().isUnauthorized());

		// Correct TOTP code issues the real access/refresh pair.
		String correctCode = totpGenerator.generate(secret, Instant.now());
		mockMvc.perform(post("/api/v1/auth/mfa/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"mfaPendingToken\":\"" + pendingToken + "\",\"code\":\"" + correctCode + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").isNotEmpty())
			.andExpect(jsonPath("$.refreshToken").isNotEmpty());
	}

	@Test
	void regression_nonAdminLogin_stillGetsRealTokensDirectly() throws Exception {
		String body = loginAndGetBody("+212703000005");

		JsonNode json = objectMapper.readTree(body);
		assertThat(json.get("accessToken").asText()).isNotBlank();
		assertThat(json.has("mfaRequired")).isFalse();
	}

	@Test
	void regression_adminWithoutMfaEnabled_stillGetsRealTokensDirectly() throws Exception {
		String adminToken = promoteToAdminAndGetToken("+212703000006");

		assertThat(adminToken).isNotBlank();
		// promoteToAdminAndGetToken already asserts the direct-token shape by extracting
		// accessToken via objectMapper without going through the mfa-pending branch.
	}

	private String activateMfa(String adminToken, String phoneNumber) throws Exception {
		MvcResult setupResult = mockMvc.perform(post("/api/v1/admin/mfa/setup")
				.header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isOk())
			.andReturn();
		String secret = objectMapper.readTree(setupResult.getResponse().getContentAsString()).get("secret").asText();

		String activationCode = totpGenerator.generate(secret, Instant.now());
		mockMvc.perform(post("/api/v1/admin/mfa/verify")
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"" + activationCode + "\"}"))
			.andExpect(status().isNoContent());
		return secret;
	}

}
