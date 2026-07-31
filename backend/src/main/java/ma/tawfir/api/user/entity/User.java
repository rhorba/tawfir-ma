package ma.tawfir.api.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Auto-created on first successful OTP verification (Sprint 2 Batch 2) — full_name
 * is nullable because no name is collected at that point; see database-tawfir.md
 * for the schema deviation from the original v1.0 doc.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	/** Shared TOTP brute-force threshold/lockout — setup-activation and login-exchange verify alike. */
	public static final int MAX_MFA_ATTEMPTS = 5;
	public static final Duration MFA_LOCKOUT_DURATION = Duration.ofMinutes(15);

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "phone_number", nullable = false, unique = true, length = 20)
	private String phoneNumber;

	@Column(name = "full_name")
	private String fullName;

	@Column(name = "national_id", length = 50)
	private String nationalId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role role;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** AES-GCM encrypted+base64 (AesGcmEncryptor), null until an admin calls the MFA setup endpoint. */
	@Column(name = "totp_secret")
	private String totpSecret;

	/** Null until the first successful MFA verify; a stored-but-unverified secret alone is not "enabled". */
	@Column(name = "totp_enabled_at")
	private Instant totpEnabledAt;

	/** Shared brute-force counter/lockout for TOTP verification (setup-activation and login-exchange alike). */
	@Column(name = "mfa_failed_attempts", nullable = false)
	private short mfaFailedAttempts;

	@Column(name = "mfa_locked_until")
	private Instant mfaLockedUntil;

	public User(String phoneNumber) {
		this.phoneNumber = phoneNumber;
		this.role = Role.MEMBER;
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	/** Stores a newly (re-)issued encrypted secret and resets activation — a fresh setup call
	 * must always be re-verified before it counts as enabled, even if a previous secret was active. */
	public void setTotpSecret(String encryptedSecret) {
		this.totpSecret = encryptedSecret;
		this.totpEnabledAt = null;
	}

	public void enableTotp() {
		this.totpEnabledAt = Instant.now();
	}

	public boolean isMfaEnabled() {
		return totpEnabledAt != null;
	}

	public boolean isMfaLocked() {
		return mfaLockedUntil != null && Instant.now().isBefore(mfaLockedUntil);
	}

	/** Records a wrong TOTP code; once {@link #MAX_MFA_ATTEMPTS} is reached, locks further attempts out for {@link #MFA_LOCKOUT_DURATION}. */
	public void recordMfaFailure() {
		this.mfaFailedAttempts++;
		if (this.mfaFailedAttempts >= MAX_MFA_ATTEMPTS) {
			this.mfaLockedUntil = Instant.now().plus(MFA_LOCKOUT_DURATION);
		}
	}

	/** A correct TOTP code clears any accumulated failures/lockout. */
	public void recordMfaSuccess() {
		this.mfaFailedAttempts = 0;
		this.mfaLockedUntil = null;
	}

}
