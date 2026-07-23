package ma.tawfir.api.otp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Local-dev stand-in for a real SMS gateway: logs the code instead of sending it.
 * Active by default ({@code tawfir.otp.provider=mock}) until a real provider is chosen.
 */
@Service
@ConditionalOnProperty(prefix = "tawfir.otp", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockOtpProvider implements OtpProvider {

	private static final Logger log = LoggerFactory.getLogger(MockOtpProvider.class);

	@Override
	public void sendOtp(String phoneNumber, String code) {
		// Dev-only: a real provider must never log the raw code (security-tawfir.md §6).
		// Phone number is masked even here since this still runs in shared dev/CI environments.
		log.info("[MOCK OTP] phone=***{} code={} (no real SMS sent)", lastDigits(phoneNumber), code);
	}

	private static String lastDigits(String phoneNumber) {
		if (phoneNumber == null || phoneNumber.length() < 4) {
			return "****";
		}
		return phoneNumber.substring(phoneNumber.length() - 4);
	}

}
