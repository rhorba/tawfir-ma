package ma.tawfir.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import ma.tawfir.api.config.TawfirProperties;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

	private static final String SIGNING_KEY = "test-only-signing-key-that-is-at-least-32-bytes-long";

	private final JwtService jwtService = new JwtService(
		new TawfirProperties(new TawfirProperties.Jwt(SIGNING_KEY, 15, 7), null, null, null, null));

	@Test
	void issueAndParse_roundTripsSubjectAndRole() {
		UUID userId = UUID.randomUUID();

		String token = jwtService.issueAccessToken(userId, "MEMBER");
		Claims claims = jwtService.parseAndValidate(token);

		assertThat(claims.getSubject()).isEqualTo(userId.toString());
		assertThat(claims.get(JwtService.ROLE_CLAIM, String.class)).isEqualTo("MEMBER");
	}

	@Test
	void parseAndValidate_rejectsTamperedToken() {
		String token = jwtService.issueAccessToken(UUID.randomUUID(), "MEMBER");
		String tampered = token.substring(0, token.length() - 1) + (token.endsWith("a") ? "b" : "a");

		assertThatThrownBy(() -> jwtService.parseAndValidate(tampered)).isInstanceOf(JwtException.class);
	}

	@Test
	void parseAndValidate_rejectsTokenSignedWithDifferentKey() {
		SecretKey otherKey = Keys.hmacShaKeyFor(
			"a-completely-different-signing-key-of-sufficient-length".getBytes());
		String token = Jwts.builder()
			.subject(UUID.randomUUID().toString())
			.issuedAt(Date.from(Instant.now()))
			.expiration(Date.from(Instant.now().plusSeconds(60)))
			.signWith(otherKey, Jwts.SIG.HS256)
			.compact();

		assertThatThrownBy(() -> jwtService.parseAndValidate(token)).isInstanceOf(JwtException.class);
	}

	@Test
	void parseAndValidate_rejectsExpiredToken() {
		SecretKey key = Keys.hmacShaKeyFor(SIGNING_KEY.getBytes());
		String expiredToken = Jwts.builder()
			.subject(UUID.randomUUID().toString())
			.issuedAt(Date.from(Instant.now().minusSeconds(120)))
			.expiration(Date.from(Instant.now().minusSeconds(60)))
			.signWith(key, Jwts.SIG.HS256)
			.compact();

		assertThatThrownBy(() -> jwtService.parseAndValidate(expiredToken)).isInstanceOf(JwtException.class);
	}

	@Test
	void accessTtlSeconds_reflectsConfiguredMinutes() {
		assertThat(jwtService.accessTtlSeconds()).isEqualTo(15 * 60);
	}

	@Test
	void issueMfaPendingToken_carriesMfaPendingClaimAndSubjectButNoRole() {
		UUID userId = UUID.randomUUID();

		String token = jwtService.issueMfaPendingToken(userId);
		Claims claims = jwtService.parseAndValidate(token);

		assertThat(claims.getSubject()).isEqualTo(userId.toString());
		assertThat(claims.get(JwtService.MFA_PENDING_CLAIM, Boolean.class)).isTrue();
		assertThat(claims.get(JwtService.ROLE_CLAIM, String.class)).isNull();
	}

	@Test
	void mfaPendingTtlSeconds_isFiveMinutes() {
		assertThat(jwtService.mfaPendingTtlSeconds()).isEqualTo(5 * 60);
	}

}
