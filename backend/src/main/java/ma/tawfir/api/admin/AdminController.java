package ma.tawfir.api.admin;

import java.util.List;
import ma.tawfir.api.admin.dto.AdminGroupResponse;
import ma.tawfir.api.admin.dto.PlatformMetricsResponse;
import ma.tawfir.api.common.ForbiddenException;
import ma.tawfir.api.dispute.dto.DisputeResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only platform-wide views for the admin dashboard (story 6.1).
 * ADMIN-only via the same manual requireAdmin pattern as MfaController —
 * not @PreAuthorize — since role-checking here is a plain global-role check
 * (no per-group membership relationship to evaluate), matching the rest of
 * this codebase's convention of explicit service/controller-layer checks.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

	private final AdminService adminService;

	public AdminController(AdminService adminService) {
		this.adminService = adminService;
	}

	@GetMapping("/groups")
	public List<AdminGroupResponse> listGroups(Authentication authentication) {
		requireAdmin(authentication);
		return adminService.listGroups();
	}

	@GetMapping("/metrics")
	public PlatformMetricsResponse metrics(Authentication authentication) {
		requireAdmin(authentication);
		return adminService.getMetrics();
	}

	@GetMapping("/disputes")
	public List<DisputeResponse> listDisputes(Authentication authentication) {
		requireAdmin(authentication);
		return adminService.listDisputes();
	}

	private void requireAdmin(Authentication authentication) {
		boolean isAdmin = authentication.getAuthorities().stream()
			.anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
		if (!isAdmin) {
			throw new ForbiddenException("Admin role required");
		}
	}

}
