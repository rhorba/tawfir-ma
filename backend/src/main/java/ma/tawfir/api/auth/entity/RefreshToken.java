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
 * token_hash is SHA-256 of the raw opaque refresh token — a high-entropy random
 * secret, unlike the OTP code, so a fast hash is appropriate (no brute-force
 * concern to justify BCrypt's cost here). replacedById marks a token that was
 * legitimately rotated; revokedAt marks explicit revocation (logout, or reuse-
 * detected family-wide revocation). See database-tawfir.md for the full rationale.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "family_id", nullable = false)
	private UUID familyId;

	@Column(name = "token_hash", nullable = false, unique = true)
	private String tokenHash;

	@Column(name = "issued_at", nullable = false)
	private Instant issuedAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "replaced_by_id")
	private UUID replacedById;

	public RefreshToken(UUID userId, UUID familyId, String tokenHash, Instant expiresAt) {
		this.userId = userId;
		this.familyId = familyId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
	}

	@PrePersist
	void onCreate() {
		this.issuedAt = Instant.now();
	}

	public boolean isExpired() {
		return Instant.now().isAfter(expiresAt);
	}

	public boolean isReused() {
		return revokedAt != null || replacedById != null;
	}

	public void revoke() {
		this.revokedAt = Instant.now();
	}

	public void markReplacedBy(UUID newTokenId) {
		this.replacedById = newTokenId;
	}

}
