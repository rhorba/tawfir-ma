package ma.tawfir.api.common;

/**
 * For business-rule validation that bean validation (@Valid) can't express —
 * e.g. rules spanning multiple fields or requiring a DB lookup. Distinct from
 * MethodArgumentNotValidException, which stays generic ("Invalid request
 * payload") for raw field-shape errors.
 */
public class ValidationException extends RuntimeException {

	public ValidationException(String message) {
		super(message);
	}

}
