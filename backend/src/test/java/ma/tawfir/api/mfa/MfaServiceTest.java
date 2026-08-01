package ma.tawfir.api.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import ma.tawfir.api.auth.RateLimitExceededException;
import ma.tawfir.api.common.AesGcmEncryptor;
import ma.tawfir.api.common.NotFoundException;
import ma.tawfir.api.common.PhoneNumberCodec;
import ma.tawfir.api.common.ValidationException;
import ma.tawfir.api.mfa.dto.MfaSetupResponse;
import ma.tawfir.api.user.UserRepository;
import ma.tawfir.api.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MfaServiceTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private AesGcmEncryptor encryptor;
	@Mock
	private PhoneNumberCodec phoneNumberCodec;
	@Mock
	private TotpGenerator totpGenerator;

	private MfaService mfaService;

	@BeforeEach
	void setUp() {
		mfaService = new MfaService(userRepository, encryptor, phoneNumberCodec, totpGenerator);
	}

	@Test
	void setup_generatesAndStoresEncryptedSecretButDoesNotEnable() {
		UUID userId = UUID.randomUUID();
		User user = new User("hashed-phone", "encrypted-phone");
		ReflectionTestUtils.setField(user, "id", userId);
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(totpGenerator.generateSecret()).thenReturn("PLAINSECRET");
		when(phoneNumberCodec.decrypt("encrypted-phone")).thenReturn("+212612345678");
		when(totpGenerator.buildProvisioningUri("+212612345678", "PLAINSECRET")).thenReturn("otpauth://totp/uri");
		when(encryptor.encrypt("PLAINSECRET")).thenReturn("encrypted-secret");

		MfaSetupResponse response = mfaService.setup(userId);

		assertThat(response.secret()).isEqualTo("PLAINSECRET");
		assertThat(response.otpauthUri()).isEqualTo("otpauth://totp/uri");
		assertThat(user.getTotpSecret()).isEqualTo("encrypted-secret");
		assertThat(user.getTotpEnabledAt()).isNull();
		verify(userRepository).save(user);
	}

	@Test
	void setup_unknownUser_throwsNotFound() {
		UUID userId = UUID.randomUUID();
		when(userRepository.findById(userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> mfaService.setup(userId)).isInstanceOf(NotFoundException.class);
	}

	@Test
	void verify_withoutPriorSetup_throwsValidationException() {
		UUID userId = UUID.randomUUID();
		User user = new User("hashed-phone", "encrypted-phone");
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));

		assertThatThrownBy(() -> mfaService.verify(userId, "123456"))
			.isInstanceOf(ValidationException.class);
	}

	@Test
	void verify_correctCode_enablesMfa() {
		UUID userId = UUID.randomUUID();
		User user = new User("hashed-phone", "encrypted-phone");
		user.setTotpSecret("encrypted-secret");
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(encryptor.decrypt("encrypted-secret")).thenReturn("plain-secret");
		when(totpGenerator.verify(eq("plain-secret"), eq("123456"), any(Instant.class))).thenReturn(true);

		mfaService.verify(userId, "123456");

		assertThat(user.getTotpEnabledAt()).isNotNull();
		verify(userRepository).save(user);
	}

	@Test
	void verify_wrongCode_throwsInvalidMfaCodeAndLeavesDisabled() {
		UUID userId = UUID.randomUUID();
		User user = new User("hashed-phone", "encrypted-phone");
		user.setTotpSecret("encrypted-secret");
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(encryptor.decrypt("encrypted-secret")).thenReturn("plain-secret");
		when(totpGenerator.verify(eq("plain-secret"), eq("000000"), any(Instant.class))).thenReturn(false);

		assertThatThrownBy(() -> mfaService.verify(userId, "000000"))
			.isInstanceOf(InvalidMfaCodeException.class);

		assertThat(user.getTotpEnabledAt()).isNull();
		assertThat(user.isMfaLocked()).isFalse();
		verify(userRepository).save(user);
	}

	@Test
	void verify_repeatedWrongCodes_locksOutAfterMaxAttempts() {
		UUID userId = UUID.randomUUID();
		User user = new User("hashed-phone", "encrypted-phone");
		user.setTotpSecret("encrypted-secret");
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(encryptor.decrypt("encrypted-secret")).thenReturn("plain-secret");
		when(totpGenerator.verify(eq("plain-secret"), eq("000000"), any(Instant.class))).thenReturn(false);

		for (int i = 0; i < User.MAX_MFA_ATTEMPTS; i++) {
			assertThatThrownBy(() -> mfaService.verify(userId, "000000"))
				.isInstanceOf(InvalidMfaCodeException.class);
		}

		assertThat(user.isMfaLocked()).isTrue();
		assertThatThrownBy(() -> mfaService.verify(userId, "000000"))
			.isInstanceOf(RateLimitExceededException.class);
	}

}
