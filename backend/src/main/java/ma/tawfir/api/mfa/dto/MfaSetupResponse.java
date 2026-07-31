package ma.tawfir.api.mfa.dto;

/**
 * {@code secret} is shown here exactly once — no other endpoint ever returns
 * it again (it's stored server-side only in encrypted form).
 */
public record MfaSetupResponse(
	String secret,
	String otpauthUri
) {
}
