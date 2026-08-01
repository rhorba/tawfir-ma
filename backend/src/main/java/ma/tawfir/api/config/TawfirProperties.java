package ma.tawfir.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "tawfir")
public record TawfirProperties(Jwt jwt, Otp otp, Payment payment, Cors cors, Pii pii) {

	public record Jwt(String signingKey, int accessTtlMinutes, int refreshTtlDays) {
	}

	public record Otp(String provider) {
	}

	public record Payment(String provider, String webhookSecret) {
	}

	public record Cors(List<String> allowedOrigins) {
	}

	/** {@code encryptionKey} is PII_ENCRYPTION_KEY (database-tawfir.md §7) — used to encrypt the
	 * admin TOTP secret at rest (AesGcmEncryptor) and phone numbers via the blind-index scheme
	 * in PhoneNumberCodec (hash for lookups, AES-GCM for reversible display). */
	public record Pii(String encryptionKey) {
	}

}
