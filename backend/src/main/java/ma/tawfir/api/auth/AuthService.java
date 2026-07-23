package ma.tawfir.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import ma.tawfir.api.auth.dto.TokenResponse;
import ma.tawfir.api.auth.entity.OtpChallenge;
import ma.tawfir.api.auth.entity.RefreshToken;
import ma.tawfir.api.config.TawfirProperties;
import ma.tawfir.api.otp.OtpProvider;
import ma.tawfir.api.user.UserRepository;
import ma.tawfir.api.user.entity.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stories 1.1-1.3 (auth-tawfir stories doc): OTP request/verify, refresh
 * rotation with reuse detection, logout. Per-IP rate limiting is intentionally
 * NOT implemented here — security-tawfir.md §3 lists it as a still-open TODO,
 * and an in-memory-only limiter wouldn't survive horizontal scaling anyway;
 * flagged rather than half-built (matches this project's convention for other
 * undecided items like the OTP/CMI provider choice).
 */
@Service
public class AuthService {

	static final int MAX_OTP_REQUESTS_PER_WINDOW = 5;
	static final Duration OTP_RATE_LIMIT_WINDOW = Duration.ofMinutes(10);
	static final int MAX_OTP_VERIFY_ATTEMPTS = 5;
	static final Duration OTP_TTL = Duration.ofMinutes(5);

	private final OtpChallengeRepository otpChallengeRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final UserRepository userRepository;
	private final OtpProvider otpProvider;
	private final JwtService jwtService;
	private final PasswordEncoder passwordEncoder;
	private final RefreshTokenRevocationService revocationService;
	private final Duration refreshTtl;
	private final SecureRandom secureRandom = new SecureRandom();

	public AuthService(OtpChallengeRepository otpChallengeRepository,
			RefreshTokenRepository refreshTokenRepository,
			UserRepository userRepository,
			OtpProvider otpProvider,
			JwtService jwtService,
			PasswordEncoder passwordEncoder,
			RefreshTokenRevocationService revocationService,
			TawfirProperties properties) {
		this.otpChallengeRepository = otpChallengeRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.userRepository = userRepository;
		this.otpProvider = otpProvider;
		this.jwtService = jwtService;
		this.passwordEncoder = passwordEncoder;
		this.revocationService = revocationService;
		this.refreshTtl = Duration.ofDays(properties.jwt().refreshTtlDays());
	}

	@Transactional
	public void requestOtp(String phoneNumber) {
		long recentCount = otpChallengeRepository.countByPhoneNumberAndCreatedAtAfter(
			phoneNumber, Instant.now().minus(OTP_RATE_LIMIT_WINDOW));
		if (recentCount >= MAX_OTP_REQUESTS_PER_WINDOW) {
			throw new RateLimitExceededException("Too many OTP requests for this phone number. Try again later.");
		}

		String code = generateOtpCode();
		OtpChallenge challenge = new OtpChallenge(phoneNumber, passwordEncoder.encode(code), Instant.now().plus(OTP_TTL));
		otpChallengeRepository.save(challenge);
		otpProvider.sendOtp(phoneNumber, code);
	}

	@Transactional
	public TokenResponse verifyOtp(String phoneNumber, String code) {
		OtpChallenge challenge = otpChallengeRepository
			.findTopByPhoneNumberAndConsumedAtIsNullOrderByCreatedAtDesc(phoneNumber)
			.orElseThrow(() -> new InvalidOtpException("Invalid or expired code"));

		if (challenge.isExpired() || challenge.getAttemptCount() >= MAX_OTP_VERIFY_ATTEMPTS) {
			throw new InvalidOtpException("Invalid or expired code");
		}

		if (!passwordEncoder.matches(code, challenge.getCodeHash())) {
			challenge.incrementAttempts();
			otpChallengeRepository.save(challenge);
			throw new InvalidOtpException("Invalid or expired code");
		}

		challenge.markConsumed();
		otpChallengeRepository.save(challenge);

		User user = userRepository.findByPhoneNumber(phoneNumber)
			.orElseGet(() -> userRepository.save(new User(phoneNumber)));

		return issueTokenPair(user, UUID.randomUUID()).response();
	}

	@Transactional
	public TokenResponse refresh(String rawRefreshToken) {
		String tokenHash = hash(rawRefreshToken);
		RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
			.orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

		if (token.isReused()) {
			revocationService.revokeFamily(token.getFamilyId());
			throw new InvalidTokenException("Refresh token reuse detected; session revoked");
		}
		if (token.isExpired()) {
			throw new InvalidTokenException("Refresh token expired");
		}

		User user = userRepository.findById(token.getUserId())
			.orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

		IssuedTokens issued = issueTokenPair(user, token.getFamilyId());
		token.markReplacedBy(issued.refreshTokenId());
		refreshTokenRepository.save(token);
		return issued.response();
	}

	public void logout(String rawRefreshToken) {
		String tokenHash = hash(rawRefreshToken);
		refreshTokenRepository.findByTokenHash(tokenHash)
			.ifPresent(token -> revocationService.revokeFamily(token.getFamilyId()));
	}

	private IssuedTokens issueTokenPair(User user, UUID familyId) {
		String accessToken = jwtService.issueAccessToken(user.getId(), user.getRole().name());

		String rawRefreshToken = generateRefreshTokenValue();
		RefreshToken refreshToken = new RefreshToken(
			user.getId(), familyId, hash(rawRefreshToken), Instant.now().plus(refreshTtl));
		refreshTokenRepository.save(refreshToken);

		TokenResponse response = TokenResponse.bearer(accessToken, rawRefreshToken, jwtService.accessTtlSeconds());
		return new IssuedTokens(response, refreshToken.getId());
	}

	private record IssuedTokens(TokenResponse response, UUID refreshTokenId) {
	}

	private String generateOtpCode() {
		int code = secureRandom.nextInt(1_000_000);
		return String.format("%06d", code);
	}

	private String generateRefreshTokenValue() {
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private String hash(String rawValue) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hashed = digest.digest(rawValue.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 must be available on the JVM", e);
		}
	}

}
