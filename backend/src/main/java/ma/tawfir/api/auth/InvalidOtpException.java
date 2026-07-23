package ma.tawfir.api.auth;

/**
 * Deliberately generic across "no challenge", "expired", "wrong code", and
 * "too many attempts" — the caller must not be able to distinguish these
 * (avoids an oracle that would help an attacker brute-force the code).
 */
public class InvalidOtpException extends RuntimeException {

	public InvalidOtpException(String message) {
		super(message);
	}

}
