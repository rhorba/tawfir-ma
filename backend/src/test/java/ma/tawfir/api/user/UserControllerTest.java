package ma.tawfir.api.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ma.tawfir.api.auth.JwtService;
import ma.tawfir.api.savings.SavingsHistoryService;
import ma.tawfir.api.savings.dto.SavingsHistoryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = UserController.class, excludeAutoConfiguration = {
	SecurityAutoConfiguration.class,
	UserDetailsServiceAutoConfiguration.class,
	SecurityFilterAutoConfiguration.class,
	ServletWebSecurityAutoConfiguration.class
})
class UserControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SavingsHistoryService savingsHistoryService;

	@MockitoBean
	private JwtService jwtService;

	private static Authentication memberAuth(UUID userId) {
		return new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
	}

	private static Authentication adminAuth(UUID userId) {
		return new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
	}

	@Test
	void savingsHistory_self_returns200() throws Exception {
		UUID userId = UUID.randomUUID();
		SavingsHistoryResponse response = new SavingsHistoryResponse(
			UUID.randomUUID(), (short) 3, BigDecimal.valueOf(100.0), (short) 0, Instant.now());
		when(savingsHistoryService.getHistoryForUser(userId)).thenReturn(List.of(response));

		mockMvc.perform(get("/api/v1/users/{userId}/savings-history", userId).principal(memberAuth(userId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].cyclesCompleted").value(3));
	}

	@Test
	void savingsHistory_otherMember_returns403() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID requesterId = UUID.randomUUID();

		mockMvc.perform(get("/api/v1/users/{userId}/savings-history", userId).principal(memberAuth(requesterId)))
			.andExpect(status().isForbidden());

		verify(savingsHistoryService, never()).getHistoryForUser(any());
	}

	@Test
	void savingsHistory_admin_canViewAnyUser_returns200() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID adminId = UUID.randomUUID();
		when(savingsHistoryService.getHistoryForUser(userId)).thenReturn(List.of());

		mockMvc.perform(get("/api/v1/users/{userId}/savings-history", userId).principal(adminAuth(adminId)))
			.andExpect(status().isOk());
	}

}
