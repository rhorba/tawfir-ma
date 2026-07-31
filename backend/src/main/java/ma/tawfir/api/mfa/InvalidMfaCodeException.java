package ma.tawfir.api.mfa;

public class InvalidMfaCodeException extends RuntimeException {

	public InvalidMfaCodeException(String message) {
		super(message);
	}

}
