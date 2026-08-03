package ma.tawfir.api.notification;

/**
 * Delivers a notification message to a phone number — organizer/member alerts for
 * late contributions (FR-5, story 3.4) and payouts needing manual review (story 4.1).
 * Real SMS/push/email provider is still an open decision, same as {@code OtpProvider}
 * (security-tawfir.md §3) — {@link MockNotificationProvider} is the default until
 * one is picked and a real implementation is wired in behind {@code tawfir.notification.provider}.
 */
public interface NotificationProvider {

	void notify(String phoneNumber, String message);

}
