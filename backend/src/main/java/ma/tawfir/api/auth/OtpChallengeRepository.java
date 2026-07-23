package ma.tawfir.api.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import ma.tawfir.api.auth.entity.OtpChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, UUID> {

	long countByPhoneNumberAndCreatedAtAfter(String phoneNumber, Instant since);

	Optional<OtpChallenge> findTopByPhoneNumberAndConsumedAtIsNullOrderByCreatedAtDesc(String phoneNumber);

}
