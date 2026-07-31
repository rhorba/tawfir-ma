package ma.tawfir.api.mfa;

import jakarta.validation.Valid;
import java.util.UUID;
import ma.tawfir.api.common.ForbiddenException;
import ma.tawfir.api.mfa.dto.MfaSetupResponse;
import ma.tawfir.api.mfa.dto.MfaVerifyRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/mfa")
public class MfaController {

	private final MfaService mfaService;

	public MfaController(MfaService mfaService) {
		this.mfaService = mfaService;
	}

	@PostMapping("/setup")
	public MfaSetupResponse setup(Authentication authentication) {
		requireAdmin(authentication);
		return mfaService.setup(userId(authentication));
	}

	@PostMapping("/verify")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void verify(@Valid @RequestBody MfaVerifyRequest request, Authentication authentication) {
		requireAdmin(authentication);
		mfaService.verify(userId(authentication), request.code());
	}

	private UUID userId(Authentication authentication) {
		return UUID.fromString(authentication.getName());
	}

	private void requireAdmin(Authentication authentication) {
		boolean isAdmin = authentication.getAuthorities().stream()
			.anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
		if (!isAdmin) {
			throw new ForbiddenException("Admin role required");
		}
	}

}
