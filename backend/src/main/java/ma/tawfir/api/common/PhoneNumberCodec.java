package ma.tawfir.api.common;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import ma.tawfir.api.config.TawfirProperties;
import org.springframework.stereotype.Component;

/**
 * Blind-index encryption for {@code users.phone_number} / {@code otp_challenges.phone_number}
 * (security-tawfir.md §5, database-tawfir.md §7): a deterministic HMAC-SHA256 {@link #hash}
 * for equality lookups (so the column can stay indexed/unique without storing plaintext),
 * plus {@link AesGcmEncryptor} passthrough for the reversible copy needed only where a phone
 * number is actually displayed back to a client (e.g. {@code MemberResponse}).
 *
 * <p>The HMAC key is derived from the same {@code PII_ENCRYPTION_KEY} as {@link AesGcmEncryptor}
 * but with a distinct domain-separation suffix, so the hash key and the AES key are different
 * even though they share one configured secret — no second env var needed.
 */
@Component
public class PhoneNumberCodec {

	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final String HASH_KEY_DOMAIN_SUFFIX = ":phone-hash";

	private final AesGcmEncryptor encryptor;
	private final SecretKeySpec hashKey;

	public PhoneNumberCodec(AesGcmEncryptor encryptor, TawfirProperties properties) {
		this.encryptor = encryptor;
		this.hashKey = deriveHashKey(properties.pii().encryptionKey());
	}

	public String encrypt(String plaintextPhoneNumber) {
		return encryptor.encrypt(plaintextPhoneNumber);
	}

	public String decrypt(String encryptedPhoneNumber) {
		return encryptor.decrypt(encryptedPhoneNumber);
	}

	/** Deterministic — same phone number always hashes to the same value, enabling equality lookups. */
	public String hash(String plaintextPhoneNumber) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(hashKey);
			byte[] hashed = mac.doFinal(plaintextPhoneNumber.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hashed);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("HMAC-SHA256 hashing failed", e);
		}
	}

	private static SecretKeySpec deriveHashKey(String rawKey) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] keyBytes = digest.digest((rawKey + HASH_KEY_DOMAIN_SUFFIX).getBytes(StandardCharsets.UTF_8));
			return new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 must be available on the JVM", e);
		}
	}

}
