package ma.tawfir.api.group;

import java.util.List;
import java.util.UUID;
import ma.tawfir.api.group.dto.PayoutResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/payouts")
public class PayoutController {

	private final PayoutScheduleService payoutScheduleService;

	public PayoutController(PayoutScheduleService payoutScheduleService) {
		this.payoutScheduleService = payoutScheduleService;
	}

	@PostMapping("/{payoutId}/execute")
	public PayoutResponse execute(@PathVariable UUID groupId, @PathVariable UUID payoutId, Authentication authentication) {
		return payoutScheduleService.executeManualOverride(userId(authentication), groupId, payoutId);
	}

	@GetMapping
	public List<PayoutResponse> list(@PathVariable UUID groupId, Authentication authentication) {
		return payoutScheduleService.listForGroup(userId(authentication), groupId, isAdmin(authentication));
	}

	private UUID userId(Authentication authentication) {
		return UUID.fromString(authentication.getName());
	}

	private boolean isAdmin(Authentication authentication) {
		return authentication.getAuthorities().stream()
			.anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
	}

}
