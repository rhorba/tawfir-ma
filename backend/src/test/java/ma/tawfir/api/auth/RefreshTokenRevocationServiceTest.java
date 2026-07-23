package ma.tawfir.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ma.tawfir.api.auth.entity.RefreshToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenRevocationServiceTest {

	@Mock
	private RefreshTokenRepository refreshTokenRepository;

	@Test
	void revokeFamily_revokesOnlyTokensNotAlreadyRevoked() {
		RefreshTokenRevocationService service = new RefreshTokenRevocationService(refreshTokenRepository);
		UUID familyId = UUID.randomUUID();
		RefreshToken active = new RefreshToken(UUID.randomUUID(), familyId, "active-hash", Instant.now().plusSeconds(3600));
		RefreshToken alreadyRevoked = new RefreshToken(
			UUID.randomUUID(), familyId, "revoked-hash", Instant.now().plusSeconds(3600));
		alreadyRevoked.revoke();
		Instant originalRevokedAt = alreadyRevoked.getRevokedAt();
		when(refreshTokenRepository.findByFamilyId(familyId)).thenReturn(List.of(active, alreadyRevoked));

		service.revokeFamily(familyId);

		assertThat(active.getRevokedAt()).isNotNull();
		assertThat(alreadyRevoked.getRevokedAt()).isEqualTo(originalRevokedAt);
		verify(refreshTokenRepository).saveAll(List.of(active, alreadyRevoked));
	}

}
