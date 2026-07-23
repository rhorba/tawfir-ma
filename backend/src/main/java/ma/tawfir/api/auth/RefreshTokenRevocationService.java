package ma.tawfir.api.auth;

import java.util.List;
import java.util.UUID;
import ma.tawfir.api.auth.entity.RefreshToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revocation runs in its own transaction (REQUIRES_NEW), committed independently
 * of the caller. Reuse-detected revocation happens inside AuthService.refresh(),
 * which then throws InvalidTokenException to reject the request — by default that
 * exception rolls back the @Transactional method it's thrown from, which would
 * silently undo the revocation along with it. This must survive that rollback.
 */
@Service
public class RefreshTokenRevocationService {

	private final RefreshTokenRepository refreshTokenRepository;

	public RefreshTokenRevocationService(RefreshTokenRepository refreshTokenRepository) {
		this.refreshTokenRepository = refreshTokenRepository;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void revokeFamily(UUID familyId) {
		List<RefreshToken> family = refreshTokenRepository.findByFamilyId(familyId);
		for (RefreshToken token : family) {
			if (token.getRevokedAt() == null) {
				token.revoke();
			}
		}
		refreshTokenRepository.saveAll(family);
	}

}
