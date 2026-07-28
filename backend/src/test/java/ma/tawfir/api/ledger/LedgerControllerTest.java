package ma.tawfir.api.ledger;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import ma.tawfir.api.auth.JwtService;
import ma.tawfir.api.common.ForbiddenException;
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

@WebMvcTest(controllers = LedgerController.class, excludeAutoConfiguration = {
	SecurityAutoConfiguration.class,
	UserDetailsServiceAutoConfiguration.class,
	SecurityFilterAutoConfiguration.class,
	ServletWebSecurityAutoConfiguration.class
})
class LedgerControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private LedgerService ledgerService;

	@MockitoBean
	private JwtService jwtService;

	private static Authentication authenticationOf(UUID userId) {
		return new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
	}

	@Test
	void list_member_returns200() throws Exception {
		UUID groupId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		when(ledgerService.listForGroup(userId, groupId, false)).thenReturn(List.of());

		mockMvc.perform(get("/api/v1/groups/{groupId}/ledger", groupId)
				.principal(authenticationOf(userId)))
			.andExpect(status().isOk());
	}

	@Test
	void list_nonMember_returns403() throws Exception {
		UUID groupId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		when(ledgerService.listForGroup(userId, groupId, false))
			.thenThrow(new ForbiddenException("You are not a member of this group"));

		mockMvc.perform(get("/api/v1/groups/{groupId}/ledger", groupId)
				.principal(authenticationOf(userId)))
			.andExpect(status().isForbidden());
	}

}
