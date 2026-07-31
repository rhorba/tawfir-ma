package ma.tawfir.api.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ma.tawfir.api.auth.dto.MfaPendingResponse;
import ma.tawfir.api.auth.dto.TokenResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Controller-layer slice only (validation, JSON wiring, status codes) — security
 * autoconfiguration is excluded here since it's exercised separately by
 * JwtAuthenticationFilterTest and the full-stack AuthFlowIntegrationTest.
 * JwtAuthenticationFilter still gets pulled into this slice regardless (@WebMvcTest
 * scans any Filter bean), so its JwtService dependency needs a stub even though
 * nothing here exercises it.
 */
@WebMvcTest(controllers = AuthController.class, excludeAutoConfiguration = {
	SecurityAutoConfiguration.class,
	UserDetailsServiceAutoConfiguration.class,
	SecurityFilterAutoConfiguration.class,
	ServletWebSecurityAutoConfiguration.class
})
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuthService authService;

	@MockitoBean
	private JwtService jwtService;

	@Test
	void requestOtp_validPhone_returns202() throws Exception {
		mockMvc.perform(post("/api/v1/auth/otp/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"phoneNumber\":\"+212612345678\"}"))
			.andExpect(status().isAccepted());

		verify(authService).requestOtp("+212612345678");
	}

	@Test
	void requestOtp_malformedPhone_returns400BeforeReachingService() throws Exception {
		mockMvc.perform(post("/api/v1/auth/otp/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"phoneNumber\":\"not-a-phone\"}"))
			.andExpect(status().isBadRequest());

		verify(authService, org.mockito.Mockito.never()).requestOtp(anyString());
	}

	@Test
	void verifyOtp_validRequest_returns200WithTokens() throws Exception {
		when(authService.verifyOtp(anyString(), anyString()))
			.thenReturn(TokenResponse.bearer("access", "refresh", 900L));

		mockMvc.perform(post("/api/v1/auth/otp/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"phoneNumber\":\"+212612345678\",\"code\":\"123456\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").value("access"))
			.andExpect(jsonPath("$.mfaRequired").doesNotExist());
	}

	@Test
	void verifyOtp_mfaEnabledAdmin_returns200WithMfaPendingResponseNotTokens() throws Exception {
		when(authService.verifyOtp(anyString(), anyString()))
			.thenReturn(MfaPendingResponse.of("pending-token", 300L));

		mockMvc.perform(post("/api/v1/auth/otp/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"phoneNumber\":\"+212612345678\",\"code\":\"123456\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mfaRequired").value(true))
			.andExpect(jsonPath("$.mfaPendingToken").value("pending-token"))
			.andExpect(jsonPath("$.accessToken").doesNotExist());
	}

	@Test
	void verifyMfa_validRequest_returns200WithTokens() throws Exception {
		when(authService.verifyMfaLogin(anyString(), anyString()))
			.thenReturn(TokenResponse.bearer("access", "refresh", 900L));

		mockMvc.perform(post("/api/v1/auth/mfa/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"mfaPendingToken\":\"pending-token\",\"code\":\"123456\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").value("access"));

		verify(authService).verifyMfaLogin("pending-token", "123456");
	}

	@Test
	void verifyMfa_malformedCode_returns400() throws Exception {
		mockMvc.perform(post("/api/v1/auth/mfa/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"mfaPendingToken\":\"pending-token\",\"code\":\"12\"}"))
			.andExpect(status().isBadRequest());

		verify(authService, org.mockito.Mockito.never()).verifyMfaLogin(anyString(), anyString());
	}

	@Test
	void verifyOtp_malformedCode_returns400() throws Exception {
		mockMvc.perform(post("/api/v1/auth/otp/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"phoneNumber\":\"+212612345678\",\"code\":\"12\"}"))
			.andExpect(status().isBadRequest());

		verify(authService, org.mockito.Mockito.never()).verifyOtp(anyString(), anyString());
	}

	@Test
	void refresh_blankToken_returns400() throws Exception {
		mockMvc.perform(post("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"\"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void logout_validRequest_returns204() throws Exception {
		mockMvc.perform(post("/api/v1/auth/logout")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"some-refresh-token\"}"))
			.andExpect(status().isNoContent());

		verify(authService).logout("some-refresh-token");
	}

}
