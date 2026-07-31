package ma.tawfir.api.user;

import java.util.List;
import java.util.UUID;
import ma.tawfir.api.common.ForbiddenException;
import ma.tawfir.api.savings.SavingsHistoryService;
import ma.tawfir.api.savings.dto.SavingsHistoryResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Story 7.1: savings history is a member's own portable record — self or
 * ADMIN only, not membership-scoped like group endpoints (a savings history
 * spans every group a user has ever been in, some of which the requester
 * may not belong to).
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

	private final SavingsHistoryService savingsHistoryService;

	public UserController(SavingsHistoryService savingsHistoryService) {
		this.savingsHistoryService = savingsHistoryService;
	}

	@GetMapping("/{userId}/savings-history")
	public List<SavingsHistoryResponse> savingsHistory(@PathVariable UUID userId, Authentication authentication) {
		requireSelfOrAdmin(userId, authentication);
		return savingsHistoryService.getHistoryForUser(userId);
	}

	private void requireSelfOrAdmin(UUID userId, Authentication authentication) {
		UUID requesterId = UUID.fromString(authentication.getName());
		boolean isAdmin = authentication.getAuthorities().stream()
			.anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
		if (!requesterId.equals(userId) && !isAdmin) {
			throw new ForbiddenException("You can only view your own savings history");
		}
	}

}
