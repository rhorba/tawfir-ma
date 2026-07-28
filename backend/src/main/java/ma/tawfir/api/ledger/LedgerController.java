package ma.tawfir.api.ledger;

import java.util.List;
import java.util.UUID;
import ma.tawfir.api.ledger.dto.LedgerEntryResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/ledger")
public class LedgerController {

	private final LedgerService ledgerService;

	public LedgerController(LedgerService ledgerService) {
		this.ledgerService = ledgerService;
	}

	@GetMapping
	public List<LedgerEntryResponse> list(@PathVariable UUID groupId, Authentication authentication) {
		return ledgerService.listForGroup(userId(authentication), groupId, isAdmin(authentication));
	}

	private UUID userId(Authentication authentication) {
		return UUID.fromString(authentication.getName());
	}

	private boolean isAdmin(Authentication authentication) {
		return authentication.getAuthorities().stream()
			.anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
	}

}
