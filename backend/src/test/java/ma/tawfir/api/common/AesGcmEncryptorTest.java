package ma.tawfir.api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ma.tawfir.api.config.TawfirProperties;
import org.junit.jupiter.api.Test;

class AesGcmEncryptorTest {

	private final AesGcmEncryptor encryptor = new AesGcmEncryptor(
		new TawfirProperties(null, null, null, null, new TawfirProperties.Pii("test-only-encryption-key"), null));

	@Test
	void encryptThenDecrypt_roundTripsPlaintext() {
		String plaintext = "JBSWY3DPEHPK3PXP";

		String ciphertext = encryptor.encrypt(plaintext);

		assertThat(ciphertext).isNotEqualTo(plaintext);
		assertThat(encryptor.decrypt(ciphertext)).isEqualTo(plaintext);
	}

	@Test
	void encrypt_isNonDeterministic_dueToRandomIv() {
		String plaintext = "same-plaintext-both-times";

		String first = encryptor.encrypt(plaintext);
		String second = encryptor.encrypt(plaintext);

		assertThat(first).isNotEqualTo(second);
		assertThat(encryptor.decrypt(first)).isEqualTo(plaintext);
		assertThat(encryptor.decrypt(second)).isEqualTo(plaintext);
	}

	@Test
	void decrypt_withDifferentKey_fails() {
		AesGcmEncryptor otherEncryptor = new AesGcmEncryptor(
			new TawfirProperties(null, null, null, null, new TawfirProperties.Pii("a-completely-different-key"), null));
		String ciphertext = encryptor.encrypt("secret-value");

		assertThatThrownBy(() -> otherEncryptor.decrypt(ciphertext))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void decrypt_tamperedCiphertext_failsAuthTagCheck() {
		String ciphertext = encryptor.encrypt("secret-value");
		byte[] bytes = java.util.Base64.getDecoder().decode(ciphertext);
		bytes[bytes.length - 1] ^= 0x01;
		String tampered = java.util.Base64.getEncoder().encodeToString(bytes);

		assertThatThrownBy(() -> encryptor.decrypt(tampered))
			.isInstanceOf(IllegalStateException.class);
	}

}
