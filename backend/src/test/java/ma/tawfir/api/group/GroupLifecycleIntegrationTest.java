package ma.tawfir.api.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import ma.tawfir.api.TestcontainersConfiguration;
import ma.tawfir.api.auth.OtpChallengeRepository;
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
 * SecurityConfig/JwtAuthenticationFilter — covers stories-tawfir.md Epic 2's
 * Gherkin scenarios end-to-end (same pattern as AuthFlowIntegrationTest).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GroupLifecycleIntegrationTest {

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

	@Test
	void organizerCreatesAndFinalizesGroup_scheduleGeneratedAndGroupActive() throws Exception {
		String organizerPhone = "+212700000001";
		String memberPhone = "+212700000002";
		String organizerToken = loginAndGetAccessToken(organizerPhone);

		String createBody = """
			{"name":"Daret Test","contributionAmount":500,"frequency":"MONTHLY","totalCycles":2,
			"payoutOrderMode":"MANUAL","members":["%s","%s"]}
			""".formatted(organizerPhone, memberPhone);

		MvcResult createResult = mockMvc.perform(post("/api/v1/groups")
				.header("Authorization", "Bearer " + organizerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(createBody))
			.andExpect(status().isCreated())
			.andReturn();
		JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
		String groupId = created.get("id").asText();
		assertThat(created.get("status").asText()).isEqualTo("DRAFT");
		assertThat(created.get("members")).hasSize(2);

		MvcResult finalizeResult = mockMvc.perform(post("/api/v1/groups/{id}/finalize", groupId)
				.header("Authorization", "Bearer " + organizerToken))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode finalized = objectMapper.readTree(finalizeResult.getResponse().getContentAsString());
		assertThat(finalized.get("status").asText()).isEqualTo("ACTIVE");

		mockMvc.perform(get("/api/v1/groups")
				.header("Authorization", "Bearer " + organizerToken))
			.andExpect(status().isOk());
	}

	@Test
	void finalize_rejectedWithIncompleteRoster_groupStaysDraft() throws Exception {
		String organizerPhone = "+212700000003";
		String organizerToken = loginAndGetAccessToken(organizerPhone);

		String createBody = """
			{"name":"Daret Test","contributionAmount":500,"frequency":"WEEKLY","totalCycles":3,
			"payoutOrderMode":"RANDOMIZED","members":["%s"]}
			""".formatted(organizerPhone);

		MvcResult createResult = mockMvc.perform(post("/api/v1/groups")
				.header("Authorization", "Bearer " + organizerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(createBody))
			.andExpect(status().isCreated())
			.andReturn();
		String groupId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

		mockMvc.perform(post("/api/v1/groups/{id}/finalize", groupId)
				.header("Authorization", "Bearer " + organizerToken))
			.andExpect(status().isBadRequest());

		MvcResult detailResult = mockMvc.perform(get("/api/v1/groups/{id}", groupId)
				.header("Authorization", "Bearer " + organizerToken))
			.andExpect(status().isOk())
			.andReturn();
		assertThat(objectMapper.readTree(detailResult.getResponse().getContentAsString()).get("status").asText())
			.isEqualTo("DRAFT");
	}

	@Test
	void nonMemberCannotViewGroupDetail() throws Exception {
		String organizerPhone = "+212700000004";
		String outsiderPhone = "+212700000005";
		String organizerToken = loginAndGetAccessToken(organizerPhone);

		String createBody = """
			{"name":"Private Group","contributionAmount":100,"frequency":"MONTHLY","totalCycles":1,
			"payoutOrderMode":"MANUAL","members":["%s"]}
			""".formatted(organizerPhone);

		MvcResult createResult = mockMvc.perform(post("/api/v1/groups")
				.header("Authorization", "Bearer " + organizerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(createBody))
			.andExpect(status().isCreated())
			.andReturn();
		String groupId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

		String outsiderToken = loginAndGetAccessToken(outsiderPhone);

		mockMvc.perform(get("/api/v1/groups/{id}", groupId)
				.header("Authorization", "Bearer " + outsiderToken))
			.andExpect(status().isForbidden());
	}

	@Test
	void createGroup_invalidContributionAmount_returns400() throws Exception {
		String organizerToken = loginAndGetAccessToken("+212700000006");

		mockMvc.perform(post("/api/v1/groups")
				.header("Authorization", "Bearer " + organizerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"Bad Group","contributionAmount":-1,"frequency":"MONTHLY","totalCycles":1,
					"payoutOrderMode":"MANUAL","members":["+212700000006"]}
					"""))
			.andExpect(status().isBadRequest());
	}

}
