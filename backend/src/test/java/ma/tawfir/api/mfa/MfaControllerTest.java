package ma.tawfir.api.mfa;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import ma.tawfir.api.auth.JwtService;
import ma.tawfir.api.mfa.dto.MfaSetupResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = MfaController.class, excludeAutoConfiguration = {
	SecurityAutoConfiguration.class,
	UserDetailsServiceAutoConfiguration.class,
	SecurityFilterAutoConfiguration.class,
	ServletWebSecurityAutoConfiguration.class
})
class MfaControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MfaService mfaService;

	@MockitoBean
	private JwtService jwtService;

	private static Authentication adminAuth(UUID userId) {
		return new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
	}

	private static Authentication memberAuth(UUID userId) {
		return new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
	}

	@Test
	void setup_admin_returns200WithSecretAndUri() throws Exception {
		UUID userId = UUID.randomUUID();
		when(mfaService.setup(userId)).thenReturn(new MfaSetupResponse("SECRET", "otpauth://totp/uri"));

		mockMvc.perform(post("/api/v1/admin/mfa/setup").principal(adminAuth(userId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.secret").value("SECRET"))
			.andExpect(jsonPath("$.otpauthUri").value("otpauth://totp/uri"));
	}

	@Test
	void setup_member_returns403() throws Exception {
		UUID userId = UUID.randomUUID();

		mockMvc.perform(post("/api/v1/admin/mfa/setup").principal(memberAuth(userId)))
			.andExpect(status().isForbidden());

		verify(mfaService, never()).setup(any());
	}

	@Test
	void verify_admin_correctCode_returns204() throws Exception {
		UUID userId = UUID.randomUUID();

		mockMvc.perform(post("/api/v1/admin/mfa/verify")
				.principal(adminAuth(userId))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"123456\"}"))
			.andExpect(status().isNoContent());

		verify(mfaService).verify(userId, "123456");
	}

	@Test
	void verify_member_returns403() throws Exception {
		UUID userId = UUID.randomUUID();

		mockMvc.perform(post("/api/v1/admin/mfa/verify")
				.principal(memberAuth(userId))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"123456\"}"))
			.andExpect(status().isForbidden());

		verify(mfaService, never()).verify(any(), any());
	}

	@Test
	void verify_malformedCode_returns400() throws Exception {
		UUID userId = UUID.randomUUID();

		mockMvc.perform(post("/api/v1/admin/mfa/verify")
				.principal(adminAuth(userId))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"12\"}"))
			.andExpect(status().isBadRequest());

		verify(mfaService, never()).verify(any(), any());
	}

}
