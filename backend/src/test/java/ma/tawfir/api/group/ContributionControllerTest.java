package ma.tawfir.api.group;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ma.tawfir.api.auth.JwtService;
import ma.tawfir.api.common.ForbiddenException;
import ma.tawfir.api.common.NotFoundException;
import ma.tawfir.api.common.ValidationException;
import ma.tawfir.api.group.dto.ContributionResponse;
import ma.tawfir.api.group.entity.ContributionStatus;
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

@WebMvcTest(controllers = ContributionController.class, excludeAutoConfiguration = {
	SecurityAutoConfiguration.class,
	UserDetailsServiceAutoConfiguration.class,
	SecurityFilterAutoConfiguration.class,
	ServletWebSecurityAutoConfiguration.class
})
class ContributionControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ContributionService contributionService;

	@MockitoBean
	private JwtService jwtService;

	private static Authentication authenticationOf(UUID userId) {
		return new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
	}

	@Test
	void markPaid_success_returns200() throws Exception {
		UUID groupId = UUID.randomUUID();
		UUID scheduleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		when(contributionService.markPaid(userId, groupId, scheduleId)).thenReturn(
			new ContributionResponse(scheduleId, groupId, (short) 1, userId, LocalDate.now(), ContributionStatus.MARKED_PAID));

		mockMvc.perform(post("/api/v1/groups/{groupId}/contributions/{scheduleId}/mark-paid", groupId, scheduleId)
				.principal(authenticationOf(userId)))
			.andExpect(status().isOk());
	}

	@Test
	void markPaid_notOwner_returns403() throws Exception {
		UUID groupId = UUID.randomUUID();
		UUID scheduleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		when(contributionService.markPaid(userId, groupId, scheduleId))
			.thenThrow(new ForbiddenException("You can only mark your own contribution as paid"));

		mockMvc.perform(post("/api/v1/groups/{groupId}/contributions/{scheduleId}/mark-paid", groupId, scheduleId)
				.principal(authenticationOf(userId)))
			.andExpect(status().isForbidden());
	}

	@Test
	void markPaid_wrongState_returns400() throws Exception {
		UUID groupId = UUID.randomUUID();
		UUID scheduleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		when(contributionService.markPaid(userId, groupId, scheduleId))
			.thenThrow(new ValidationException("Contribution is not in a payable state"));

		mockMvc.perform(post("/api/v1/groups/{groupId}/contributions/{scheduleId}/mark-paid", groupId, scheduleId)
				.principal(authenticationOf(userId)))
			.andExpect(status().isBadRequest());
	}

	@Test
	void confirm_success_returns200() throws Exception {
		UUID groupId = UUID.randomUUID();
		UUID scheduleId = UUID.randomUUID();
		UUID organizerId = UUID.randomUUID();
		when(contributionService.confirm(organizerId, groupId, scheduleId)).thenReturn(
			new ContributionResponse(scheduleId, groupId, (short) 1, UUID.randomUUID(), LocalDate.now(), ContributionStatus.CONFIRMED));

		mockMvc.perform(post("/api/v1/groups/{groupId}/contributions/{scheduleId}/confirm", groupId, scheduleId)
				.principal(authenticationOf(organizerId)))
			.andExpect(status().isOk());
	}

	@Test
	void confirm_notFound_returns404() throws Exception {
		UUID groupId = UUID.randomUUID();
		UUID scheduleId = UUID.randomUUID();
		UUID organizerId = UUID.randomUUID();
		when(contributionService.confirm(organizerId, groupId, scheduleId))
			.thenThrow(new NotFoundException("Contribution not found: " + scheduleId));

		mockMvc.perform(post("/api/v1/groups/{groupId}/contributions/{scheduleId}/confirm", groupId, scheduleId)
				.principal(authenticationOf(organizerId)))
			.andExpect(status().isNotFound());
	}

	@Test
	void list_returns200() throws Exception {
		UUID groupId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		when(contributionService.listForGroup(userId, groupId, false)).thenReturn(List.of());

		mockMvc.perform(get("/api/v1/groups/{groupId}/contributions", groupId)
				.principal(authenticationOf(userId)))
			.andExpect(status().isOk());
	}

	@Test
	void list_nonMember_returns403() throws Exception {
		UUID groupId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		when(contributionService.listForGroup(userId, groupId, false))
			.thenThrow(new ForbiddenException("You are not a member of this group"));

		mockMvc.perform(get("/api/v1/groups/{groupId}/contributions", groupId)
				.principal(authenticationOf(userId)))
			.andExpect(status().isForbidden());
	}

}
