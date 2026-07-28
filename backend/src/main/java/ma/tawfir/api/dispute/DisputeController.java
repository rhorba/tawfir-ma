package ma.tawfir.api.dispute;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import ma.tawfir.api.dispute.dto.DisputeResponse;
import ma.tawfir.api.dispute.dto.OpenDisputeRequest;
import ma.tawfir.api.dispute.dto.ResolveDisputeRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DisputeController {

	private final DisputeService disputeService;

	public DisputeController(DisputeService disputeService) {
		this.disputeService = disputeService;
	}

	@PostMapping("/api/v1/groups/{groupId}/disputes")
	@ResponseStatus(HttpStatus.CREATED)
	public DisputeResponse openDispute(@PathVariable UUID groupId, @Valid @RequestBody OpenDisputeRequest request,
			Authentication authentication) {
		return disputeService.openDispute(userId(authentication), groupId, request, isAdmin(authentication));
	}

	@GetMapping("/api/v1/groups/{groupId}/disputes")
	public List<DisputeResponse> listForGroup(@PathVariable UUID groupId, Authentication authentication) {
		return disputeService.listForGroup(userId(authentication), groupId, isAdmin(authentication));
	}

	@PatchMapping("/api/v1/disputes/{disputeId}/resolve")
	public DisputeResponse resolveDispute(@PathVariable UUID disputeId, @Valid @RequestBody ResolveDisputeRequest request,
			Authentication authentication) {
		return disputeService.resolveDispute(userId(authentication), disputeId, request, isAdmin(authentication));
	}

	private UUID userId(Authentication authentication) {
		return UUID.fromString(authentication.getName());
	}

	private boolean isAdmin(Authentication authentication) {
		return authentication.getAuthorities().stream()
			.anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
	}

}
