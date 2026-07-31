package ma.tawfir.api.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import ma.tawfir.api.admin.dto.AdminGroupResponse;
import ma.tawfir.api.admin.dto.PlatformMetricsResponse;
import ma.tawfir.api.auth.JwtService;
import ma.tawfir.api.dispute.dto.DisputeResponse;
import ma.tawfir.api.dispute.entity.DisputeStatus;
import ma.tawfir.api.group.entity.GroupStatus;
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

@WebMvcTest(controllers = AdminController.class, excludeAutoConfiguration = {
	SecurityAutoConfiguration.class,
	UserDetailsServiceAutoConfiguration.class,
	SecurityFilterAutoConfiguration.class,
	ServletWebSecurityAutoConfiguration.class
})
class AdminControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AdminService adminService;

	@MockitoBean
	private JwtService jwtService;

	private static Authentication adminAuth() {
		return new UsernamePasswordAuthenticationToken(UUID.randomUUID().toString(), null,
			List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
	}

	private static Authentication memberAuth() {
		return new UsernamePasswordAuthenticationToken(UUID.randomUUID().toString(), null,
			List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
	}

	@Test
	void listGroups_admin_returns200() throws Exception {
		UUID groupId = UUID.randomUUID();
		when(adminService.listGroups()).thenReturn(
			List.of(new AdminGroupResponse(groupId, "Tontine A", GroupStatus.ACTIVE, 5, (short) 6, 2)));

		mockMvc.perform(get("/api/v1/admin/groups").principal(adminAuth()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(groupId.toString()))
			.andExpect(jsonPath("$[0].name").value("Tontine A"))
			.andExpect(jsonPath("$[0].memberCount").value(5));
	}

	@Test
	void listGroups_member_returns403() throws Exception {
		mockMvc.perform(get("/api/v1/admin/groups").principal(memberAuth()))
			.andExpect(status().isForbidden());

		verify(adminService, never()).listGroups();
	}

	@Test
	void metrics_admin_returns200() throws Exception {
		when(adminService.getMetrics()).thenReturn(new PlatformMetricsResponse(5, 25.0, 3, 2));

		mockMvc.perform(get("/api/v1/admin/metrics").principal(adminAuth()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.activeGroups").value(5))
			.andExpect(jsonPath("$.defaultRatePercent").value(25.0))
			.andExpect(jsonPath("$.openDisputes").value(3))
			.andExpect(jsonPath("$.atRiskGroups").value(2));
	}

	@Test
	void metrics_member_returns403() throws Exception {
		mockMvc.perform(get("/api/v1/admin/metrics").principal(memberAuth()))
			.andExpect(status().isForbidden());

		verify(adminService, never()).getMetrics();
	}

	@Test
	void listDisputes_admin_returns200() throws Exception {
		DisputeResponse dispute = new DisputeResponse(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
			UUID.randomUUID(), "reason", null, DisputeStatus.OPEN, null, null, null, null);
		when(adminService.listDisputes()).thenReturn(List.of(dispute));

		mockMvc.perform(get("/api/v1/admin/disputes").principal(adminAuth()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(dispute.id().toString()));
	}

	@Test
	void listDisputes_member_returns403() throws Exception {
		mockMvc.perform(get("/api/v1/admin/disputes").principal(memberAuth()))
			.andExpect(status().isForbidden());

		verify(adminService, never()).listDisputes();
	}

}
