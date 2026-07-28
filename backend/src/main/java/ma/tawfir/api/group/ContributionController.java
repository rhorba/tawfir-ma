package ma.tawfir.api.group;

import java.util.List;
import java.util.UUID;
import ma.tawfir.api.group.dto.ContributionResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/contributions")
public class ContributionController {

	private final ContributionService contributionService;

	public ContributionController(ContributionService contributionService) {
		this.contributionService = contributionService;
	}

	@PostMapping("/{scheduleId}/mark-paid")
	public ContributionResponse markPaid(@PathVariable UUID groupId, @PathVariable UUID scheduleId,
			Authentication authentication) {
		return contributionService.markPaid(userId(authentication), groupId, scheduleId);
	}

	@PostMapping("/{scheduleId}/confirm")
	public ContributionResponse confirm(@PathVariable UUID groupId, @PathVariable UUID scheduleId,
			Authentication authentication) {
		return contributionService.confirm(userId(authentication), groupId, scheduleId);
	}

	@GetMapping
	public List<ContributionResponse> list(@PathVariable UUID groupId, Authentication authentication) {
		return contributionService.listForGroup(userId(authentication), groupId, isAdmin(authentication));
	}

	private UUID userId(Authentication authentication) {
		return UUID.fromString(authentication.getName());
	}

	private boolean isAdmin(Authentication authentication) {
		return authentication.getAuthorities().stream()
			.anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
	}

}
