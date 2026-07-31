package ma.tawfir.api.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads a Bearer access token, if present, and populates the SecurityContext.
 * Any parse/signature/expiry failure is treated as "no authentication" rather
 * than an error response here — the downstream authorization check
 * (SecurityConfig's .anyRequest().authenticated()) is what turns that into 401.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtService jwtService;

	public JwtAuthenticationFilter(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String header = request.getHeader("Authorization");
		if (header != null && header.startsWith(BEARER_PREFIX)) {
			String token = header.substring(BEARER_PREFIX.length());
			try {
				Claims claims = jwtService.parseAndValidate(token);
				if (Boolean.TRUE.equals(claims.get(JwtService.MFA_PENDING_CLAIM, Boolean.class))) {
					// An MFA-pending token proves OTP was verified but not yet the TOTP step —
					// it must never authenticate a normal request (story 1.4).
					SecurityContextHolder.clearContext();
				} else {
					String role = claims.get(JwtService.ROLE_CLAIM, String.class);
					List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
					Authentication authentication =
						new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
					SecurityContextHolder.getContext().setAuthentication(authentication);
				}
			} catch (JwtException | IllegalArgumentException ex) {
				SecurityContextHolder.clearContext();
			}
		}
		filterChain.doFilter(request, response);
	}

}
