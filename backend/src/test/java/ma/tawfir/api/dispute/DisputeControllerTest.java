package ma.tawfir.api.dispute;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ma.tawfir.api.auth.JwtService;
import ma.tawfir.api.common.ForbiddenException;
import ma.tawfir.api.common.ValidationException;
import ma.tawfir.api.dispute.dto.DisputeResponse;
import ma.tawfir.api.dispute.entity.DisputeStatus;
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

@WebMvcTest(controllers = DisputeController.class, excludeAutoConfiguration = {
	SecurityAutoConfiguration.class,
	UserDetailsServiceAutoConfiguration.class,
	SecurityFilterAutoConfiguration.class,
	ServletWebSecurityAutoConfiguration.class
})
class DisputeControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DisputeService disputeService;

	@MockitoBean
	private JwtService jwtService;

	private static Authentication authenticationOf(UUID userId) {
		return new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
	}

	private static DisputeResponse sampleResponse(UUID groupId, UUID userId, DisputeStatus status) {
		return new DisputeResponse(UUID.randomUUID(), groupId, UUID.randomUUID(), userId, "wrong amount", null,
			status, null, null, Instant.now(), null);
	}

	@Test
	void openDispute_success_returns201() throws Exception {
		UUID groupId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		when(disputeService.openDispute(eq(userId), eq(groupId), any(), eq(false)))
			.thenReturn(sampleResponse(groupId, userId, DisputeStatus.OPEN));

		mockMvc.perform(post("/api/v1/groups/{groupId}/disputes", groupId)
				.principal(authenticationOf(userId))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"ledgerEntryId\":\"" + UUID.randomUUID() + "\",\"reason\":\"wrong amount\"}"))
			.andExpect(status().isCreated());
	}

	@Test
	void openDispute_blankReason_returns400() throws Exception {
		UUID groupId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		mockMvc.perform(post("/api/v1/groups/{groupId}/disputes", groupId)
				.principal(authenticationOf(userId))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"ledgerEntryId\":\"" + UUID.randomUUID() + "\",\"reason\":\"\"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void openDispute_nonMember_returns403() throws Exception {
		UUID groupId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		when(disputeService.openDispute(eq(userId), eq(groupId), any(), eq(false)))
			.thenThrow(new ForbiddenException("You are not a member of this group"));

		mockMvc.perform(post("/api/v1/groups/{groupId}/disputes", groupId)
				.principal(authenticationOf(userId))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"ledgerEntryId\":\"" + UUID.randomUUID() + "\",\"reason\":\"wrong amount\"}"))
			.andExpect(status().isForbidden());
	}

	@Test
	void listForGroup_member_returns200() throws Exception {
		UUID groupId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		when(disputeService.listForGroup(userId, groupId, false)).thenReturn(List.of());

		mockMvc.perform(get("/api/v1/groups/{groupId}/disputes", groupId)
				.principal(authenticationOf(userId)))
			.andExpect(status().isOk());
	}

	@Test
	void listForGroup_nonMember_returns403() throws Exception {
		UUID groupId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		when(disputeService.listForGroup(userId, groupId, false))
			.thenThrow(new ForbiddenException("You are not a member of this group"));

		mockMvc.perform(get("/api/v1/groups/{groupId}/disputes", groupId)
				.principal(authenticationOf(userId)))
			.andExpect(status().isForbidden());
	}

	@Test
	void resolveDispute_success_returns200() throws Exception {
		UUID disputeId = UUID.randomUUID();
		UUID organizerId = UUID.randomUUID();
		when(disputeService.resolveDispute(eq(organizerId), eq(disputeId), any(), anyBoolean()))
			.thenReturn(sampleResponse(UUID.randomUUID(), UUID.randomUUID(), DisputeStatus.ACCEPTED));

		mockMvc.perform(patch("/api/v1/disputes/{disputeId}/resolve", disputeId)
				.principal(authenticationOf(organizerId))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"resolution\":\"ACCEPTED\",\"resolutionReason\":\"confirmed error\"}"))
			.andExpect(status().isOk());
	}

	@Test
	void resolveDispute_notOrganizer_returns403() throws Exception {
		UUID disputeId = UUID.randomUUID();
		UUID memberId = UUID.randomUUID();
		when(disputeService.resolveDispute(eq(memberId), eq(disputeId), any(), anyBoolean()))
			.thenThrow(new ForbiddenException("Only the group organizer can resolve a dispute"));

		mockMvc.perform(patch("/api/v1/disputes/{disputeId}/resolve", disputeId)
				.principal(authenticationOf(memberId))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"resolution\":\"ACCEPTED\",\"resolutionReason\":\"confirmed error\"}"))
			.andExpect(status().isForbidden());
	}

	@Test
	void resolveDispute_alreadyResolved_returns400() throws Exception {
		UUID disputeId = UUID.randomUUID();
		UUID organizerId = UUID.randomUUID();
		when(disputeService.resolveDispute(eq(organizerId), eq(disputeId), any(), anyBoolean()))
			.thenThrow(new ValidationException("Dispute has already been resolved"));

		mockMvc.perform(patch("/api/v1/disputes/{disputeId}/resolve", disputeId)
				.principal(authenticationOf(organizerId))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"resolution\":\"REJECTED\",\"resolutionReason\":\"no error found\"}"))
			.andExpect(status().isBadRequest());
	}

}
