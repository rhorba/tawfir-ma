package ma.tawfir.api.mfa;

import java.time.Instant;
import java.util.UUID;
import ma.tawfir.api.auth.RateLimitExceededException;
import ma.tawfir.api.common.AesGcmEncryptor;
import ma.tawfir.api.common.NotFoundException;
import ma.tawfir.api.common.ValidationException;
import ma.tawfir.api.mfa.dto.MfaSetupResponse;
import ma.tawfir.api.user.UserRepository;
import ma.tawfir.api.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin self-service TOTP setup/activation (story 1.4). Setup stores an
 * encrypted secret but leaves the account MFA-inactive ({@code totpEnabledAt}
 * null) until the admin proves possession of it via a first successful verify
 * — a stored-but-unproven secret must never be enough to satisfy MFA on its
 * own. See security-tawfir.md §3: no backup codes, DB-level recovery only.
 */
@Service
public class MfaService {

	private final UserRepository userRepository;
	private final AesGcmEncryptor encryptor;
	private final TotpGenerator totpGenerator;

	public MfaService(UserRepository userRepository, AesGcmEncryptor encryptor, TotpGenerator totpGenerator) {
		this.userRepository = userRepository;
		this.encryptor = encryptor;
		this.totpGenerator = totpGenerator;
	}

	@Transactional
	public MfaSetupResponse setup(UUID userId) {
		User user = findUser(userId);
		String secret = totpGenerator.generateSecret();
		user.setTotpSecret(encryptor.encrypt(secret));
		userRepository.save(user);

		String otpauthUri = totpGenerator.buildProvisioningUri(user.getPhoneNumber(), secret);
		return new MfaSetupResponse(secret, otpauthUri);
	}

	@Transactional
	public void verify(UUID userId, String code) {
		User user = findUser(userId);
		if (user.getTotpSecret() == null) {
			throw new ValidationException("TOTP has not been set up for this account; call setup first");
		}
		if (user.isMfaLocked()) {
			throw new RateLimitExceededException("Too many incorrect codes; try again later.");
		}

		String secret = encryptor.decrypt(user.getTotpSecret());
		if (!totpGenerator.verify(secret, code, Instant.now())) {
			user.recordMfaFailure();
			userRepository.save(user);
			throw new InvalidMfaCodeException("Invalid TOTP code");
		}

		user.recordMfaSuccess();
		user.enableTotp();
		userRepository.save(user);
	}

	private User findUser(UUID userId) {
		return userRepository.findById(userId)
			.orElseThrow(() -> new NotFoundException("User not found: " + userId));
	}

}
