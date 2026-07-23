package ma.tawfir.api.otp;

/**
 * Delivers an OTP code to a phone number. Real SMS provider is still an open
 * decision (security-tawfir.md §3) — {@link MockOtpProvider} is the default until
 * one is picked and a real implementation is wired in behind {@code tawfir.otp.provider}.
 */
public interface OtpProvider {

	void sendOtp(String phoneNumber, String code);

}
