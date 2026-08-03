package ma.tawfir.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import ma.tawfir.api.config.TawfirProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

	private static final String SIGNING_KEY = "test-only-signing-key-that-is-at-least-32-bytes-long";

	private final JwtService jwtService = new JwtService(
		new TawfirProperties(new TawfirProperties.Jwt(SIGNING_KEY, 15, 7), null, null, null, null, null));
	private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void validBearerToken_setsAuthenticationWithRoleAuthority() throws Exception {
		UUID userId = UUID.randomUUID();
		String token = jwtService.issueAccessToken(userId, "ADMIN");

		HttpServletRequest request = mock(HttpServletRequest.class);
		org.mockito.Mockito.when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
		HttpServletResponse response = mock(HttpServletResponse.class);
		FilterChain chain = mock(FilterChain.class);

		filter.doFilterInternal(request, response, chain);

		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		assertThat(auth).isNotNull();
		assertThat(auth.getName()).isEqualTo(userId.toString());
		assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
		verify(chain).doFilter(request, response);
	}

	@Test
	void malformedToken_clearsContextAndContinuesChain() throws Exception {
		HttpServletRequest request = mock(HttpServletRequest.class);
		org.mockito.Mockito.when(request.getHeader("Authorization")).thenReturn("Bearer not-a-real-jwt");
		HttpServletResponse response = mock(HttpServletResponse.class);
		FilterChain chain = mock(FilterChain.class);

		filter.doFilterInternal(request, response, chain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verify(chain).doFilter(request, response);
	}

	@Test
	void noAuthorizationHeader_leavesContextEmptyAndContinuesChain() throws Exception {
		HttpServletRequest request = mock(HttpServletRequest.class);
		org.mockito.Mockito.when(request.getHeader("Authorization")).thenReturn(null);
		HttpServletResponse response = mock(HttpServletResponse.class);
		FilterChain chain = mock(FilterChain.class);

		filter.doFilterInternal(request, response, chain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verify(chain).doFilter(request, response);
	}

	@Test
	void mfaPendingToken_isNeverAuthenticated_evenThoughSignatureIsValid() throws Exception {
		String token = jwtService.issueMfaPendingToken(UUID.randomUUID());

		HttpServletRequest request = mock(HttpServletRequest.class);
		org.mockito.Mockito.when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
		HttpServletResponse response = mock(HttpServletResponse.class);
		FilterChain chain = mock(FilterChain.class);

		filter.doFilterInternal(request, response, chain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verify(chain).doFilter(request, response);
	}

}
