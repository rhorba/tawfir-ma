package ma.tawfir.api.group;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ma.tawfir.api.auth.JwtService;
import ma.tawfir.api.common.ForbiddenException;
import ma.tawfir.api.common.NotFoundException;
import ma.tawfir.api.common.ValidationException;
import ma.tawfir.api.group.dto.PayoutResponse;
import ma.tawfir.api.group.entity.PayoutStatus;
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

@WebMvcTest(controllers = PayoutController.class, excludeAutoConfiguration = {
	SecurityAutoConfiguration.class,
	UserDetailsServiceAutoConfiguration.class,
	SecurityFilterAutoConfiguration.class,
	ServletWebSecurityAutoConfiguration.class
})
class PayoutControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PayoutScheduleService payoutScheduleService;

	@MockitoBean
	private JwtService jwtService;

	private static Authentication authenticationOf(UUID userId) {
		return new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
	}

	@Test
	void execute_organizerOverride_returns200() throws Exception {
		UUID groupId = UUID.randomUUID();
		UUID payoutId = UUID.randomUUID();
		UUID organizerId = UUID.randomUUID();
		when(payoutScheduleService.executeManualOverride(organizerId, groupId, payoutId)).thenReturn(
			new PayoutResponse(payoutId, groupId, (short) 1, UUID.randomUUID(), LocalDate.now(),
				BigDecimal.valueOf(500), PayoutStatus.MANUAL_OVERRIDE));

		mockMvc.perform(post("/api/v1/groups/{groupId}/payouts/{payoutId}/execute", groupId, payoutId)
				.principal(authenticationOf(organizerId)))
			.andExpect(status().isOk());
	}

	@Test
	void execute_nonOrganizer_returns403() throws Exception {
		UUID groupId = UUID.randomUUID();
		UUID payoutId = UUID.randomUUID();
		UUID memberId = UUID.randomUUID();
		when(payoutScheduleService.executeManualOverride(memberId, groupId, payoutId))
			.thenThrow(new ForbiddenException("Only the group organizer can execute a payout"));

		mockMvc.perform(post("/api/v1/groups/{groupId}/payouts/{payoutId}/execute", groupId, payoutId)
				.principal(authenticationOf(memberId)))
			.andExpect(status().isForbidden());
	}

	@Test
	void execute_notFound_returns404() throws Exception {
		UUID groupId = UUID.randomUUID();
		UUID payoutId = UUID.randomUUID();
		UUID organizerId = UUID.randomUUID();
		when(payoutScheduleService.executeManualOverride(organizerId, groupId, payoutId))
			.thenThrow(new NotFoundException("Payout not found: " + payoutId));

		mockMvc.perform(post("/api/v1/groups/{groupId}/payouts/{payoutId}/execute", groupId, payoutId)
				.principal(authenticationOf(organizerId)))
			.andExpect(status().isNotFound());
	}

	@Test
	void execute_alreadyExecuted_returns400() throws Exception {
		UUID groupId = UUID.randomUUID();
		UUID payoutId = UUID.randomUUID();
		UUID organizerId = UUID.randomUUID();
		when(payoutScheduleService.executeManualOverride(organizerId, groupId, payoutId))
			.thenThrow(new ValidationException("Payout is not in an overridable state"));

		mockMvc.perform(post("/api/v1/groups/{groupId}/payouts/{payoutId}/execute", groupId, payoutId)
				.principal(authenticationOf(organizerId)))
			.andExpect(status().isBadRequest());
	}

	@Test
	void list_returns200() throws Exception {
		UUID groupId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		when(payoutScheduleService.listForGroup(userId, groupId, false)).thenReturn(List.of());

		mockMvc.perform(get("/api/v1/groups/{groupId}/payouts", groupId)
				.principal(authenticationOf(userId)))
			.andExpect(status().isOk());
	}

	@Test
	void list_nonMember_returns403() throws Exception {
		UUID groupId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		when(payoutScheduleService.listForGroup(userId, groupId, false))
			.thenThrow(new ForbiddenException("You are not a member of this group"));

		mockMvc.perform(get("/api/v1/groups/{groupId}/payouts", groupId)
				.principal(authenticationOf(userId)))
			.andExpect(status().isForbidden());
	}

}
