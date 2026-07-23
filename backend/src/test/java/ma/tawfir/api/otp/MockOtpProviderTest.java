package ma.tawfir.api.otp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class MockOtpProviderTest {

	private final MockOtpProvider provider = new MockOtpProvider();

	@Test
	void sendOtp_doesNotThrow_forValidPhoneNumber() {
		assertDoesNotThrow(() -> provider.sendOtp("+212612345678", "123456"));
	}

	@Test
	void sendOtp_doesNotThrow_forShortOrNullPhoneNumber() {
		assertDoesNotThrow(() -> provider.sendOtp("123", "123456"));
		assertDoesNotThrow(() -> provider.sendOtp(null, "123456"));
	}

}
