package ma.tawfir.api.auth.dto;

/**
 * What POST /api/v1/auth/otp/verify returns: either the real {@link TokenResponse}
 * (the unchanged, unaffected-by-MFA case — non-admins and MFA-disabled admins),
 * or a {@link MfaPendingResponse} for an MFA-enabled admin (story 1.4). Sealed so
 * the two concrete JSON shapes stay exhaustive and callers can't invent a third.
 */
public sealed interface OtpVerifyResult permits TokenResponse, MfaPendingResponse {
}
