package ma.tawfir.api.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * code_hash is a BCrypt hash of the raw 6-digit code — the raw code is only ever
 * held in memory and passed to the OtpProvider, never persisted (security-tawfir.md §6).
 * phone_number_hash is a deterministic HMAC-SHA256 blind index (PhoneNumberCodec) — no
 * reversible copy is stored here since nothing ever reads a challenge's phone number back.
 */
@Entity
@Table(name = "otp_challenges")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OtpChallenge {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "phone_number_hash", nullable = false, length = 64)
	private String phoneNumberHash;

	@Column(name = "code_hash", nullable = false)
	private String codeHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "consumed_at")
	private Instant consumedAt;

	@Column(name = "attempt_count", nullable = false)
	private short attemptCount;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public OtpChallenge(String phoneNumberHash, String codeHash, Instant expiresAt) {
		this.phoneNumberHash = phoneNumberHash;
		this.codeHash = codeHash;
		this.expiresAt = expiresAt;
		this.attemptCount = 0;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public boolean isExpired() {
		return Instant.now().isAfter(expiresAt);
	}

	public boolean isConsumed() {
		return consumedAt != null;
	}

	public void markConsumed() {
		this.consumedAt = Instant.now();
	}

	public void incrementAttempts() {
		this.attemptCount++;
	}

}
