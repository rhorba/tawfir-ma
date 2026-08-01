package ma.tawfir.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import ma.tawfir.api.auth.dto.MfaPendingResponse;
import ma.tawfir.api.auth.dto.OtpVerifyResult;
import ma.tawfir.api.auth.dto.TokenResponse;
import ma.tawfir.api.auth.entity.OtpChallenge;
import ma.tawfir.api.auth.entity.RefreshToken;
import ma.tawfir.api.common.AesGcmEncryptor;
import ma.tawfir.api.common.PhoneNumberCodec;
import ma.tawfir.api.config.TawfirProperties;
import ma.tawfir.api.mfa.InvalidMfaCodeException;
import ma.tawfir.api.mfa.TotpGenerator;
import ma.tawfir.api.otp.OtpProvider;
import ma.tawfir.api.user.UserRepository;
import ma.tawfir.api.user.entity.Role;
import ma.tawfir.api.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	private static final String PHONE = "+212612345678";
	private static final String HASHED_PHONE = "hashed-" + PHONE;

	@Mock
	private OtpChallengeRepository otpChallengeRepository;
	@Mock
	private RefreshTokenRepository refreshTokenRepository;
	@Mock
	private UserRepository userRepository;
	@Mock
	private OtpProvider otpProvider;
	@Mock
	private PasswordEncoder passwordEncoder;
	@Mock
	private RefreshTokenRevocationService revocationService;
	@Mock
	private AesGcmEncryptor totpEncryptor;
	@Mock
	private PhoneNumberCodec phoneNumberCodec;
	@Mock
	private TotpGenerator totpGenerator;

	private JwtService jwtService;
	private AuthService authService;

	@BeforeEach
	void setUp() {
		jwtService = mock(JwtService.class);
		TawfirProperties properties = new TawfirProperties(
			new TawfirProperties.Jwt("unused-in-this-test", 15, 7), null, null, null, null);
		authService = new AuthService(otpChallengeRepository, refreshTokenRepository, userRepository,
			otpProvider, jwtService, passwordEncoder, revocationService, totpEncryptor, phoneNumberCodec,
			totpGenerator, properties);
	}

	@Test
	void requestOtp_ratelimited_afterFiveInWindow() {
		when(phoneNumberCodec.hash(PHONE)).thenReturn(HASHED_PHONE);
		when(otpChallengeRepository.countByPhoneNumberHashAndCreatedAtAfter(eq(HASHED_PHONE), any()))
			.thenReturn(5L);

		assertThatThrownBy(() -> authService.requestOtp(PHONE))
			.isInstanceOf(RateLimitExceededException.class);

		verify(otpProvider, never()).sendOtp(any(), any());
		verify(otpChallengeRepository, never()).save(any());
	}

	@Test
	void requestOtp_success_savesChallengeAndSendsCode() {
		when(phoneNumberCodec.hash(PHONE)).thenReturn(HASHED_PHONE);
		when(otpChallengeRepository.countByPhoneNumberHashAndCreatedAtAfter(eq(HASHED_PHONE), any()))
			.thenReturn(0L);
		when(passwordEncoder.encode(anyString())).thenReturn("hashed-code");

		authService.requestOtp(PHONE);

		verify(otpChallengeRepository).save(any(OtpChallenge.class));
		ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
		verify(otpProvider).sendOtp(eq(PHONE), codeCaptor.capture());
		assertThat(codeCaptor.getValue()).matches("\\d{6}");
	}

	@Test
	void verifyOtp_noChallengeFound_throwsInvalidOtp() {
		when(phoneNumberCodec.hash(PHONE)).thenReturn(HASHED_PHONE);
		when(otpChallengeRepository.findTopByPhoneNumberHashAndConsumedAtIsNullOrderByCreatedAtDesc(HASHED_PHONE))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.verifyOtp(PHONE, "123456"))
			.isInstanceOf(InvalidOtpException.class);
	}

	@Test
	void verifyOtp_expiredChallenge_throwsInvalidOtp() {
		when(phoneNumberCodec.hash(PHONE)).thenReturn(HASHED_PHONE);
		OtpChallenge challenge = new OtpChallenge(HASHED_PHONE, "hash", Instant.now().minusSeconds(1));
		when(otpChallengeRepository.findTopByPhoneNumberHashAndConsumedAtIsNullOrderByCreatedAtDesc(HASHED_PHONE))
			.thenReturn(Optional.of(challenge));

		assertThatThrownBy(() -> authService.verifyOtp(PHONE, "123456"))
			.isInstanceOf(InvalidOtpException.class);
	}

	@Test
	void verifyOtp_maxAttemptsExceeded_throwsInvalidOtp() {
		when(phoneNumberCodec.hash(PHONE)).thenReturn(HASHED_PHONE);
		OtpChallenge challenge = new OtpChallenge(HASHED_PHONE, "hash", Instant.now().plusSeconds(300));
		for (int i = 0; i < AuthService.MAX_OTP_VERIFY_ATTEMPTS; i++) {
			challenge.incrementAttempts();
		}
		when(otpChallengeRepository.findTopByPhoneNumberHashAndConsumedAtIsNullOrderByCreatedAtDesc(HASHED_PHONE))
			.thenReturn(Optional.of(challenge));

		assertThatThrownBy(() -> authService.verifyOtp(PHONE, "123456"))
			.isInstanceOf(InvalidOtpException.class);
	}

	@Test
	void verifyOtp_wrongCode_incrementsAttemptsAndThrows() {
		when(phoneNumberCodec.hash(PHONE)).thenReturn(HASHED_PHONE);
		OtpChallenge challenge = new OtpChallenge(HASHED_PHONE, "hash", Instant.now().plusSeconds(300));
		when(otpChallengeRepository.findTopByPhoneNumberHashAndConsumedAtIsNullOrderByCreatedAtDesc(HASHED_PHONE))
			.thenReturn(Optional.of(challenge));
		when(passwordEncoder.matches("000000", "hash")).thenReturn(false);

		assertThatThrownBy(() -> authService.verifyOtp(PHONE, "000000"))
			.isInstanceOf(InvalidOtpException.class);

		assertThat(challenge.getAttemptCount()).isEqualTo((short) 1);
		verify(otpChallengeRepository).save(challenge);
	}

	@Test
	void verifyOtp_success_createsNewUserAndIssuesTokens() {
		when(phoneNumberCodec.hash(PHONE)).thenReturn(HASHED_PHONE);
		OtpChallenge challenge = new OtpChallenge(HASHED_PHONE, "hash", Instant.now().plusSeconds(300));
		when(otpChallengeRepository.findTopByPhoneNumberHashAndConsumedAtIsNullOrderByCreatedAtDesc(HASHED_PHONE))
			.thenReturn(Optional.of(challenge));
		when(passwordEncoder.matches("123456", "hash")).thenReturn(true);
		when(userRepository.findByPhoneNumberHash(HASHED_PHONE)).thenReturn(Optional.empty());
		User newUser = new User(HASHED_PHONE, "encrypted-phone");
		when(userRepository.save(any(User.class))).thenReturn(newUser);
		when(jwtService.issueAccessToken(any(), eq("MEMBER"))).thenReturn("access-token");
		when(jwtService.accessTtlSeconds()).thenReturn(900L);

		OtpVerifyResult result = authService.verifyOtp(PHONE, "123456");

		assertThat(result).isInstanceOf(TokenResponse.class);
		TokenResponse response = (TokenResponse) result;
		assertThat(response.accessToken()).isEqualTo("access-token");
		assertThat(response.refreshToken()).isNotBlank();
		assertThat(response.tokenType()).isEqualTo("Bearer");
		assertThat(challenge.isConsumed()).isTrue();
		verify(refreshTokenRepository).save(any(RefreshToken.class));
	}

	@Test
	void verifyOtp_success_reusesExistingUser() {
		when(phoneNumberCodec.hash(PHONE)).thenReturn(HASHED_PHONE);
		OtpChallenge challenge = new OtpChallenge(HASHED_PHONE, "hash", Instant.now().plusSeconds(300));
		when(otpChallengeRepository.findTopByPhoneNumberHashAndConsumedAtIsNullOrderByCreatedAtDesc(HASHED_PHONE))
			.thenReturn(Optional.of(challenge));
		when(passwordEncoder.matches("123456", "hash")).thenReturn(true);
		User existingUser = new User(HASHED_PHONE, "encrypted-phone");
		when(userRepository.findByPhoneNumberHash(HASHED_PHONE)).thenReturn(Optional.of(existingUser));
		when(jwtService.issueAccessToken(any(), any())).thenReturn("access-token");
		when(jwtService.accessTtlSeconds()).thenReturn(900L);

		authService.verifyOtp(PHONE, "123456");

		verify(userRepository, never()).save(any(User.class));
	}

	@Test
	void refresh_success_rotatesTokenKeepingFamily() {
		UUID familyId = UUID.randomUUID();
		User user = new User(HASHED_PHONE, "encrypted-phone");
		RefreshToken existing = new RefreshToken(user.getId(), familyId, "old-hash", Instant.now().plusSeconds(3600));
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(existing));
		when(userRepository.findById(any())).thenReturn(Optional.of(user));
		when(jwtService.issueAccessToken(any(), any())).thenReturn("new-access-token");
		when(jwtService.accessTtlSeconds()).thenReturn(900L);
		// save() normally lets Hibernate assign the generated UUID id in-place; simulate that
		// here since a mocked repository otherwise leaves the new token's id null.
		when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
			RefreshToken token = invocation.getArgument(0);
			if (token.getId() == null) {
				ReflectionTestUtils.setField(token, "id", UUID.randomUUID());
			}
			return token;
		});

		TokenResponse response = authService.refresh("raw-old-token");

		assertThat(response.accessToken()).isEqualTo("new-access-token");
		assertThat(existing.getReplacedById()).isNotNull();
		verify(refreshTokenRepository).save(existing);
		verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
		verify(revocationService, never()).revokeFamily(any());
	}

	@Test
	void refresh_reusedToken_revokesFamilyThroughDedicatedServiceAndThrows() {
		UUID familyId = UUID.randomUUID();
		RefreshToken reused = new RefreshToken(UUID.randomUUID(), familyId, "reused-hash", Instant.now().plusSeconds(3600));
		reused.markReplacedBy(UUID.randomUUID());
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(reused));

		assertThatThrownBy(() -> authService.refresh("stolen-raw-token"))
			.isInstanceOf(InvalidTokenException.class);

		// Delegated to a REQUIRES_NEW-transactional service so the revocation survives
		// the rollback that this method's own thrown exception triggers.
		verify(revocationService).revokeFamily(familyId);
	}

	@Test
	void refresh_expiredToken_throwsWithoutRevokingFamily() {
		RefreshToken expired = new RefreshToken(
			UUID.randomUUID(), UUID.randomUUID(), "hash", Instant.now().minusSeconds(1));
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

		assertThatThrownBy(() -> authService.refresh("expired-raw-token"))
			.isInstanceOf(InvalidTokenException.class);

		verify(revocationService, never()).revokeFamily(any());
	}

	@Test
	void refresh_unknownToken_throwsInvalidToken() {
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.refresh("unknown-raw-token"))
			.isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void refresh_userNoLongerExists_throwsInvalidToken() {
		RefreshToken token = new RefreshToken(
			UUID.randomUUID(), UUID.randomUUID(), "hash", Instant.now().plusSeconds(3600));
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
		when(userRepository.findById(any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.refresh("raw-token"))
			.isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void logout_delegatesRevocationToDedicatedService() {
		UUID familyId = UUID.randomUUID();
		RefreshToken token = new RefreshToken(UUID.randomUUID(), familyId, "hash", Instant.now().plusSeconds(3600));
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

		authService.logout("raw-token");

		verify(revocationService).revokeFamily(familyId);
	}

	@Test
	void logout_unknownToken_isIdempotentNoop() {
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

		authService.logout("unknown-raw-token");

		verify(revocationService, never()).revokeFamily(any());
	}

	@Test
	void verifyOtp_adminWithMfaEnabled_returnsMfaPendingResponseNotTokens() {
		when(phoneNumberCodec.hash(PHONE)).thenReturn(HASHED_PHONE);
		OtpChallenge challenge = new OtpChallenge(HASHED_PHONE, "hash", Instant.now().plusSeconds(300));
		when(otpChallengeRepository.findTopByPhoneNumberHashAndConsumedAtIsNullOrderByCreatedAtDesc(HASHED_PHONE))
			.thenReturn(Optional.of(challenge));
		when(passwordEncoder.matches("123456", "hash")).thenReturn(true);
		User admin = new User(HASHED_PHONE, "encrypted-phone");
		ReflectionTestUtils.setField(admin, "role", Role.ADMIN);
		ReflectionTestUtils.setField(admin, "totpEnabledAt", Instant.now());
		when(userRepository.findByPhoneNumberHash(HASHED_PHONE)).thenReturn(Optional.of(admin));
		when(jwtService.issueMfaPendingToken(any())).thenReturn("pending-token");
		when(jwtService.mfaPendingTtlSeconds()).thenReturn(300L);

		OtpVerifyResult result = authService.verifyOtp(PHONE, "123456");

		assertThat(result).isInstanceOf(MfaPendingResponse.class);
		MfaPendingResponse pending = (MfaPendingResponse) result;
		assertThat(pending.mfaRequired()).isTrue();
		assertThat(pending.mfaPendingToken()).isEqualTo("pending-token");
		assertThat(pending.expiresInSeconds()).isEqualTo(300L);
		verify(jwtService, never()).issueAccessToken(any(), any());
		verify(refreshTokenRepository, never()).save(any());
	}

	@Test
	void verifyOtp_adminWithoutMfaEnabled_stillReturnsTokensDirectly() {
		when(phoneNumberCodec.hash(PHONE)).thenReturn(HASHED_PHONE);
		OtpChallenge challenge = new OtpChallenge(HASHED_PHONE, "hash", Instant.now().plusSeconds(300));
		when(otpChallengeRepository.findTopByPhoneNumberHashAndConsumedAtIsNullOrderByCreatedAtDesc(HASHED_PHONE))
			.thenReturn(Optional.of(challenge));
		when(passwordEncoder.matches("123456", "hash")).thenReturn(true);
		User admin = new User(HASHED_PHONE, "encrypted-phone");
		ReflectionTestUtils.setField(admin, "role", Role.ADMIN);
		when(userRepository.findByPhoneNumberHash(HASHED_PHONE)).thenReturn(Optional.of(admin));
		when(jwtService.issueAccessToken(any(), eq("ADMIN"))).thenReturn("access-token");
		when(jwtService.accessTtlSeconds()).thenReturn(900L);

		OtpVerifyResult result = authService.verifyOtp(PHONE, "123456");

		assertThat(result).isInstanceOf(TokenResponse.class);
		assertThat(((TokenResponse) result).accessToken()).isEqualTo("access-token");
		verify(jwtService, never()).issueMfaPendingToken(any());
	}

	@Test
	void verifyMfaLogin_correctCode_issuesRealTokens() {
		UUID userId = UUID.randomUUID();
		User admin = new User(HASHED_PHONE, "encrypted-phone");
		ReflectionTestUtils.setField(admin, "id", userId);
		ReflectionTestUtils.setField(admin, "role", Role.ADMIN);
		ReflectionTestUtils.setField(admin, "totpEnabledAt", Instant.now());
		ReflectionTestUtils.setField(admin, "totpSecret", "encrypted-secret");
		when(userRepository.findById(userId)).thenReturn(Optional.of(admin));

		Claims claims = mock(Claims.class);
		when(claims.get(JwtService.MFA_PENDING_CLAIM, Boolean.class)).thenReturn(true);
		when(claims.getSubject()).thenReturn(userId.toString());
		when(jwtService.parseAndValidate("pending-token")).thenReturn(claims);
		when(totpEncryptor.decrypt("encrypted-secret")).thenReturn("plain-secret");
		when(totpGenerator.verify(eq("plain-secret"), eq("123456"), any())).thenReturn(true);
		when(jwtService.issueAccessToken(eq(userId), eq("ADMIN"))).thenReturn("access-token");
		when(jwtService.accessTtlSeconds()).thenReturn(900L);

		TokenResponse response = authService.verifyMfaLogin("pending-token", "123456");

		assertThat(response.accessToken()).isEqualTo("access-token");
		verify(refreshTokenRepository).save(any(RefreshToken.class));
	}

	@Test
	void verifyMfaLogin_wrongCode_throwsInvalidMfaCodeWithoutIssuingTokens() {
		UUID userId = UUID.randomUUID();
		User admin = new User(HASHED_PHONE, "encrypted-phone");
		ReflectionTestUtils.setField(admin, "id", userId);
		ReflectionTestUtils.setField(admin, "role", Role.ADMIN);
		ReflectionTestUtils.setField(admin, "totpEnabledAt", Instant.now());
		ReflectionTestUtils.setField(admin, "totpSecret", "encrypted-secret");
		when(userRepository.findById(userId)).thenReturn(Optional.of(admin));

		Claims claims = mock(Claims.class);
		when(claims.get(JwtService.MFA_PENDING_CLAIM, Boolean.class)).thenReturn(true);
		when(claims.getSubject()).thenReturn(userId.toString());
		when(jwtService.parseAndValidate("pending-token")).thenReturn(claims);
		when(totpEncryptor.decrypt("encrypted-secret")).thenReturn("plain-secret");
		when(totpGenerator.verify(eq("plain-secret"), eq("000000"), any())).thenReturn(false);

		assertThatThrownBy(() -> authService.verifyMfaLogin("pending-token", "000000"))
			.isInstanceOf(InvalidMfaCodeException.class);

		verify(refreshTokenRepository, never()).save(any());
	}

	@Test
	void verifyMfaLogin_repeatedWrongCodes_locksOutAfterMaxAttempts() {
		UUID userId = UUID.randomUUID();
		User admin = new User(HASHED_PHONE, "encrypted-phone");
		ReflectionTestUtils.setField(admin, "id", userId);
		ReflectionTestUtils.setField(admin, "role", Role.ADMIN);
		ReflectionTestUtils.setField(admin, "totpEnabledAt", Instant.now());
		ReflectionTestUtils.setField(admin, "totpSecret", "encrypted-secret");
		when(userRepository.findById(userId)).thenReturn(Optional.of(admin));

		Claims claims = mock(Claims.class);
		when(claims.get(JwtService.MFA_PENDING_CLAIM, Boolean.class)).thenReturn(true);
		when(claims.getSubject()).thenReturn(userId.toString());
		when(jwtService.parseAndValidate("pending-token")).thenReturn(claims);
		when(totpEncryptor.decrypt("encrypted-secret")).thenReturn("plain-secret");
		when(totpGenerator.verify(eq("plain-secret"), eq("000000"), any())).thenReturn(false);

		for (int i = 0; i < User.MAX_MFA_ATTEMPTS; i++) {
			assertThatThrownBy(() -> authService.verifyMfaLogin("pending-token", "000000"))
				.isInstanceOf(InvalidMfaCodeException.class);
		}

		assertThat(admin.isMfaLocked()).isTrue();
		assertThatThrownBy(() -> authService.verifyMfaLogin("pending-token", "000000"))
			.isInstanceOf(RateLimitExceededException.class);
		verify(refreshTokenRepository, never()).save(any());
	}

	@Test
	void verifyMfaLogin_tokenMissingMfaPendingClaim_throwsInvalidToken() {
		Claims claims = mock(Claims.class);
		when(claims.get(JwtService.MFA_PENDING_CLAIM, Boolean.class)).thenReturn(null);
		when(jwtService.parseAndValidate("not-a-pending-token")).thenReturn(claims);

		assertThatThrownBy(() -> authService.verifyMfaLogin("not-a-pending-token", "123456"))
			.isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void verifyMfaLogin_malformedToken_throwsInvalidToken() {
		when(jwtService.parseAndValidate("garbage")).thenThrow(new JwtException("bad token"));

		assertThatThrownBy(() -> authService.verifyMfaLogin("garbage", "123456"))
			.isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void verifyMfaLogin_userNoLongerEligible_throwsInvalidToken() {
		UUID userId = UUID.randomUUID();
		Claims claims = mock(Claims.class);
		when(claims.get(JwtService.MFA_PENDING_CLAIM, Boolean.class)).thenReturn(true);
		when(claims.getSubject()).thenReturn(userId.toString());
		when(jwtService.parseAndValidate("pending-token")).thenReturn(claims);
		when(userRepository.findById(userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.verifyMfaLogin("pending-token", "123456"))
			.isInstanceOf(InvalidTokenException.class);
	}

}
