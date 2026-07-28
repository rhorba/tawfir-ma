package ma.tawfir.api.common;

import java.time.Instant;
import ma.tawfir.api.auth.InvalidOtpException;
import ma.tawfir.api.auth.InvalidTokenException;
import ma.tawfir.api.auth.RateLimitExceededException;
import ma.tawfir.api.group.GroupNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Error responses never include stack traces or raw exception messages for
 * unexpected failures (security-tawfir.md: Information Disclosure) — only the
 * handful of known, intentionally-user-facing exception types below surface
 * their message text.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(RateLimitExceededException.class)
	public ResponseEntity<ApiError> handleRateLimit(RateLimitExceededException ex) {
		return build(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
	}

	@ExceptionHandler(InvalidOtpException.class)
	public ResponseEntity<ApiError> handleInvalidOtp(InvalidOtpException ex) {
		return build(HttpStatus.UNAUTHORIZED, ex.getMessage());
	}

	@ExceptionHandler(InvalidTokenException.class)
	public ResponseEntity<ApiError> handleInvalidToken(InvalidTokenException ex) {
		return build(HttpStatus.UNAUTHORIZED, ex.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
		return build(HttpStatus.BAD_REQUEST, "Invalid request payload");
	}

	@ExceptionHandler(ValidationException.class)
	public ResponseEntity<ApiError> handleValidation(ValidationException ex) {
		return build(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	@ExceptionHandler(ForbiddenException.class)
	public ResponseEntity<ApiError> handleForbidden(ForbiddenException ex) {
		return build(HttpStatus.FORBIDDEN, ex.getMessage());
	}

	@ExceptionHandler(GroupNotFoundException.class)
	public ResponseEntity<ApiError> handleNotFound(GroupNotFoundException ex) {
		return build(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(NotFoundException.class)
	public ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
		return build(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
		log.error("Unhandled exception", ex);
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
	}

	private ResponseEntity<ApiError> build(HttpStatus status, String message) {
		ApiError body = new ApiError(status.value(), status.getReasonPhrase(), message, Instant.now());
		return ResponseEntity.status(status).body(body);
	}

}
