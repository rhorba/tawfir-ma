package ma.tawfir.api.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ma.tawfir.api.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	List<RefreshToken> findByFamilyId(UUID familyId);

}
