package ma.tawfir.api.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Local-dev stand-in for a real SMS/push/email gateway: logs the message instead of
 * sending it. Active by default ({@code tawfir.notification.provider=mock}) until a
 * real provider is chosen.
 */
@Service
@ConditionalOnProperty(prefix = "tawfir.notification", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockNotificationProvider implements NotificationProvider {

	private static final Logger log = LoggerFactory.getLogger(MockNotificationProvider.class);

	@Override
	public void notify(String phoneNumber, String message) {
		// Phone number is masked even here since this still runs in shared dev/CI environments.
		log.info("[MOCK NOTIFICATION] phone=***{} message=\"{}\" (no real SMS/push/email sent)",
			lastDigits(phoneNumber), message);
	}

	private static String lastDigits(String phoneNumber) {
		if (phoneNumber == null || phoneNumber.length() < 4) {
			return "****";
		}
		return phoneNumber.substring(phoneNumber.length() - 4);
	}

}
