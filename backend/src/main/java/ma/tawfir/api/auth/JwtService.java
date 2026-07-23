package ma.tawfir.api.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import ma.tawfir.api.config.TawfirProperties;
import org.springframework.stereotype.Component;

/**
 * Signs and verifies access tokens with HS256 only (ADR-3) — jjwt's parser is
 * bound to this one key/algorithm, so an "alg: none" or algorithm-confusion
 * token is rejected by construction, not by a runtime check we could forget.
 */
@Component
public class JwtService {

	public static final String ROLE_CLAIM = "role";

	private final SecretKey signingKey;
	private final Duration accessTtl;

	public JwtService(TawfirProperties properties) {
		this.signingKey = Keys.hmacShaKeyFor(properties.jwt().signingKey().getBytes(StandardCharsets.UTF_8));
		this.accessTtl = Duration.ofMinutes(properties.jwt().accessTtlMinutes());
	}

	public String issueAccessToken(UUID userId, String role) {
		Instant now = Instant.now();
		return Jwts.builder()
			.subject(userId.toString())
			.claim(ROLE_CLAIM, role)
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plus(accessTtl)))
			.signWith(signingKey, Jwts.SIG.HS256)
			.compact();
	}

	public long accessTtlSeconds() {
		return accessTtl.toSeconds();
	}

	/**
	 * Throws {@link io.jsonwebtoken.JwtException} (or a subtype) for any
	 * malformed, expired, or signature-invalid token — callers must not swallow
	 * this into a generic 500; it means "not authenticated".
	 */
	public Claims parseAndValidate(String token) {
		return Jwts.parser()
			.verifyWith(signingKey)
			.build()
			.parseSignedClaims(token)
			.getPayload();
	}

}
