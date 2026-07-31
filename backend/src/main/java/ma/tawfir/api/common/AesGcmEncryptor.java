package ma.tawfir.api.common;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import ma.tawfir.api.config.TawfirProperties;
import org.springframework.stereotype.Component;

/**
 * Application-layer AES-256-GCM encryption for at-rest secrets that need real
 * confidentiality (not just a hash) — first user: the admin TOTP secret
 * (database-tawfir.md §3, security-tawfir.md §3). Reuses PII_ENCRYPTION_KEY
 * (already reserved in .env.example for the deferred phone-number encryption)
 * since a TOTP secret only needs encrypt/decrypt, not the blind-index/equality
 * lookup that phone_number will eventually need — no reason to wait on that
 * larger piece of work to unblock this one.
 *
 * <p>The configured key is a raw string secret (same pattern as
 * {@code JWT_SIGNING_KEY} elsewhere in this codebase), SHA-256-hashed here to
 * get a fixed 256-bit AES key regardless of the configured string's length.
 * A random 96-bit IV is generated per encryption and prepended to the
 * ciphertext before base64 encoding, the standard AES-GCM storage layout.
 */
@Component
public class AesGcmEncryptor {

	private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
	private static final String KEY_ALGORITHM = "AES";
	private static final int IV_LENGTH_BYTES = 12;
	private static final int TAG_LENGTH_BITS = 128;

	private final SecretKeySpec key;
	private final SecureRandom secureRandom = new SecureRandom();

	public AesGcmEncryptor(TawfirProperties properties) {
		this.key = deriveKey(properties.pii().encryptionKey());
	}

	public String encrypt(String plaintext) {
		try {
			byte[] iv = new byte[IV_LENGTH_BYTES];
			secureRandom.nextBytes(iv);

			Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
			byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

			byte[] combined = new byte[iv.length + ciphertext.length];
			System.arraycopy(iv, 0, combined, 0, iv.length);
			System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
			return Base64.getEncoder().encodeToString(combined);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("AES-GCM encryption failed", e);
		}
	}

	public String decrypt(String encoded) {
		try {
			byte[] combined = Base64.getDecoder().decode(encoded);
			byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH_BYTES);
			byte[] ciphertext = Arrays.copyOfRange(combined, IV_LENGTH_BYTES, combined.length);

			Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
			byte[] plaintext = cipher.doFinal(ciphertext);
			return new String(plaintext, StandardCharsets.UTF_8);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("AES-GCM decryption failed", e);
		}
	}

	private static SecretKeySpec deriveKey(String rawKey) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] keyBytes = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
			return new SecretKeySpec(keyBytes, KEY_ALGORITHM);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 must be available on the JVM", e);
		}
	}

}
