package ma.tawfir.api.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * SecurityConfig/JwtAuthenticationFilter. Covers story 6.1's Gherkin scenario
 * (member gets 403 on an admin-only endpoint) plus an admin-succeeds smoke
 * check across all three endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminFlowIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private OtpChallengeRepository otpChallengeRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void cleanOtpChallenges() {
		otpChallengeRepository.deleteAll();
	}

	private String loginAndGetToken(String phoneNumber) throws Exception {
		String code = "654321";
		otpChallengeRepository.save(new OtpChallenge(phoneNumber, passwordEncoder.encode(code), Instant.now().plusSeconds(300)));

		MvcResult result = mockMvc.perform(post("/api/v1/auth/otp/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"phoneNumber\":\"" + phoneNumber + "\",\"code\":\"" + code + "\"}"))
			.andExpect(status().isOk())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
	}

	private String promoteToAdminAndGetToken(String phoneNumber) throws Exception {
		loginAndGetToken(phoneNumber);
		User user = userRepository.findByPhoneNumber(phoneNumber).orElseThrow();
		ReflectionTestUtils.setField(user, "role", Role.ADMIN);
		userRepository.save(user);

		// Re-login: the first token above still carries the pre-promotion MEMBER role claim.
		return loginAndGetToken(phoneNumber);
	}

	@Test
	void metrics_memberRole_returns403() throws Exception {
		String memberToken = loginAndGetToken("+212704000001");

		mockMvc.perform(get("/api/v1/admin/metrics").header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isForbidden());
	}

	@Test
	void groups_memberRole_returns403() throws Exception {
		String memberToken = loginAndGetToken("+212704000002");

		mockMvc.perform(get("/api/v1/admin/groups").header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isForbidden());
	}

	@Test
	void disputes_memberRole_returns403() throws Exception {
		String memberToken = loginAndGetToken("+212704000003");

		mockMvc.perform(get("/api/v1/admin/disputes").header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isForbidden());
	}

	@Test
	void adminRole_canReachAllThreeEndpoints() throws Exception {
		String adminToken = promoteToAdminAndGetToken("+212704000004");

		mockMvc.perform(get("/api/v1/admin/groups").header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/admin/metrics").header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.activeGroups").exists())
			.andExpect(jsonPath("$.defaultRatePercent").exists())
			.andExpect(jsonPath("$.openDisputes").exists())
			.andExpect(jsonPath("$.atRiskGroups").exists());

		mockMvc.perform(get("/api/v1/admin/disputes").header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isOk());
	}

}
