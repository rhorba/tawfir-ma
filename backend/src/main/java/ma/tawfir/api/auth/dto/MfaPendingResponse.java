package ma.tawfir.api.auth.dto;

/**
 * Returned by POST /api/v1/auth/otp/verify instead of {@link TokenResponse} when
 * the resolved user is an MFA-enabled admin (story 1.4). {@code mfaPendingToken}
 * must be exchanged, together with a TOTP code, at POST /api/v1/auth/mfa/verify
 * for the real access/refresh pair — it is not a usable access token itself.
 */
public record MfaPendingResponse(
	boolean mfaRequired,
	String mfaPendingToken,
	long expiresInSeconds
) implements OtpVerifyResult {

	public static MfaPendingResponse of(String mfaPendingToken, long expiresInSeconds) {
		return new MfaPendingResponse(true, mfaPendingToken, expiresInSeconds);
	}

}
