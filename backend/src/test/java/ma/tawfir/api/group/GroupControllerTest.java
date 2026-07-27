package ma.tawfir.api.group;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import ma.tawfir.api.auth.JwtService;
import ma.tawfir.api.common.ForbiddenException;
import ma.tawfir.api.common.ValidationException;
import ma.tawfir.api.group.dto.GroupDetailResponse;
import ma.tawfir.api.group.entity.Frequency;
import ma.tawfir.api.group.entity.Group;
import ma.tawfir.api.group.entity.GroupStatus;
import ma.tawfir.api.group.entity.PayoutOrderMode;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Controller-layer slice only — security autoconfiguration is excluded (see
 * AuthControllerTest for the same pattern). The authenticated caller is
 * injected via MockHttpServletRequestBuilder#principal(Principal) directly,
 * since the filter that would normally populate HttpServletRequest's
 * principal from the SecurityContext (SecurityContextHolderAwareRequestFilter)
 * is part of the excluded chain.
 */
@WebMvcTest(controllers = GroupController.class, excludeAutoConfiguration = {
	SecurityAutoConfiguration.class,
	UserDetailsServiceAutoConfiguration.class,
	SecurityFilterAutoConfiguration.class,
	ServletWebSecurityAutoConfiguration.class
})
class GroupControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private GroupService groupService;

	@MockitoBean
	private JwtService jwtService;

	private static Authentication authenticationOf(UUID userId, String role) {
		return new UsernamePasswordAuthenticationToken(
			userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
	}

	@Test
	void createGroup_validRequest_returns201() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID groupId = UUID.randomUUID();
		Group group = new Group("Daret", userId, BigDecimal.valueOf(500), Frequency.MONTHLY, (short) 2, PayoutOrderMode.MANUAL);
		ReflectionTestUtils.setField(group, "id", groupId);
		when(groupService.createGroup(eq(userId), any())).thenReturn(group);
		when(groupService.getGroupDetail(eq(userId), eq(groupId), eq(false))).thenReturn(
			new GroupDetailResponse(groupId, "Daret", userId, BigDecimal.valueOf(500), "MAD",
				Frequency.MONTHLY, (short) 2, PayoutOrderMode.MANUAL, GroupStatus.DRAFT, List.of()));

		mockMvc.perform(post("/api/v1/groups")
				.principal(authenticationOf(userId, "MEMBER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"Daret","contributionAmount":500,"frequency":"MONTHLY","totalCycles":2,
					"payoutOrderMode":"MANUAL","members":["+212612345678"]}
					"""))
			.andExpect(status().isCreated());
	}

	@Test
	void createGroup_invalidAmount_returns400BeforeReachingService() throws Exception {
		mockMvc.perform(post("/api/v1/groups")
				.principal(authenticationOf(UUID.randomUUID(), "MEMBER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"Daret","contributionAmount":-5,"frequency":"MONTHLY","totalCycles":2,
					"payoutOrderMode":"MANUAL","members":["+212612345678"]}
					"""))
			.andExpect(status().isBadRequest());

		verify(groupService, org.mockito.Mockito.never()).createGroup(any(), any());
	}

	@Test
	void finalizeGroup_notOrganizer_returns403() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID groupId = UUID.randomUUID();
		when(groupService.finalizeGroup(userId, groupId))
			.thenThrow(new ForbiddenException("Only the group organizer can finalize this group"));

		mockMvc.perform(post("/api/v1/groups/{groupId}/finalize", groupId)
				.principal(authenticationOf(userId, "MEMBER")))
			.andExpect(status().isForbidden());
	}

	@Test
	void finalizeGroup_memberCountMismatch_returns400() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID groupId = UUID.randomUUID();
		when(groupService.finalizeGroup(userId, groupId))
			.thenThrow(new ValidationException("Member count must equal total_cycles to finalize"));

		mockMvc.perform(post("/api/v1/groups/{groupId}/finalize", groupId)
				.principal(authenticationOf(userId, "MEMBER")))
			.andExpect(status().isBadRequest());
	}

	@Test
	void getGroup_nonMember_returns403() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID groupId = UUID.randomUUID();
		when(groupService.getGroupDetail(userId, groupId, false))
			.thenThrow(new ForbiddenException("You are not a member of this group"));

		mockMvc.perform(get("/api/v1/groups/{groupId}", groupId)
				.principal(authenticationOf(userId, "MEMBER")))
			.andExpect(status().isForbidden());
	}

	@Test
	void getGroup_admin_passesAdminFlagThrough() throws Exception {
		UUID adminId = UUID.randomUUID();
		UUID groupId = UUID.randomUUID();
		when(groupService.getGroupDetail(adminId, groupId, true)).thenReturn(
			new GroupDetailResponse(groupId, "Daret", UUID.randomUUID(), BigDecimal.valueOf(500), "MAD",
				Frequency.MONTHLY, (short) 2, PayoutOrderMode.MANUAL, GroupStatus.ACTIVE, List.of()));

		mockMvc.perform(get("/api/v1/groups/{groupId}", groupId)
				.principal(authenticationOf(adminId, "ADMIN")))
			.andExpect(status().isOk());
	}

	@Test
	void getGroup_notFound_returns404() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID groupId = UUID.randomUUID();
		when(groupService.getGroupDetail(userId, groupId, false))
			.thenThrow(new GroupNotFoundException("Group not found: " + groupId));

		mockMvc.perform(get("/api/v1/groups/{groupId}", groupId)
				.principal(authenticationOf(userId, "MEMBER")))
			.andExpect(status().isNotFound());
	}

	@Test
	void listGroups_returns200() throws Exception {
		UUID userId = UUID.randomUUID();
		when(groupService.listGroupsForUser(userId)).thenReturn(List.of());

		mockMvc.perform(get("/api/v1/groups")
				.principal(authenticationOf(userId, "MEMBER")))
			.andExpect(status().isOk());
	}

}
