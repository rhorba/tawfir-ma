package ma.tawfir.api.group;

import java.time.LocalDate;
import java.util.List;
import ma.tawfir.api.common.PhoneNumberCodec;
import ma.tawfir.api.group.entity.PayoutSchedule;
import ma.tawfir.api.notification.NotificationProvider;
import ma.tawfir.api.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Story 4.1: executes due payouts automatically. Each payout is its own
 * transaction (PayoutScheduleService#executeIfReady) so one CMI failure
 * doesn't roll back the others in the same run. Payouts left PENDING (not
 * ready) or FAILED (transfer error) notify the group's organizer
 * (NotificationProvider — mocked until a real provider is chosen,
 * security-tawfir.md §3) so manual override isn't discovered only by chance
 * next time they load the group.
 */
@Component
public class PayoutScheduler {

	private static final Logger log = LoggerFactory.getLogger(PayoutScheduler.class);

	private final PayoutScheduleRepository payoutScheduleRepository;
	private final PayoutScheduleService payoutScheduleService;
	private final GroupRepository groupRepository;
	private final UserRepository userRepository;
	private final PhoneNumberCodec phoneNumberCodec;
	private final NotificationProvider notificationProvider;

	public PayoutScheduler(PayoutScheduleRepository payoutScheduleRepository,
			PayoutScheduleService payoutScheduleService, GroupRepository groupRepository,
			UserRepository userRepository, PhoneNumberCodec phoneNumberCodec,
			NotificationProvider notificationProvider) {
		this.payoutScheduleRepository = payoutScheduleRepository;
		this.payoutScheduleService = payoutScheduleService;
		this.groupRepository = groupRepository;
		this.userRepository = userRepository;
		this.phoneNumberCodec = phoneNumberCodec;
		this.notificationProvider = notificationProvider;
	}

	@Scheduled(cron = "0 0 * * * *")
	public void executeDuePayouts() {
		List<PayoutSchedule> due = payoutScheduleRepository.findDuePending(LocalDate.now());
		int executed = 0;
		int notReady = 0;
		for (PayoutSchedule payout : due) {
			try {
				if (payoutScheduleService.executeIfReady(payout)) {
					executed++;
				} else {
					notReady++;
					log.warn("Payout {} (group {}, cycle {}) is due but not all contributions are CONFIRMED yet "
						+ "— needs organizer manual override", payout.getId(), payout.getGroupId(), payout.getCycleNumber());
					notifyOrganizer(payout, "isn't ready yet (not all contributions confirmed)");
				}
			} catch (RuntimeException e) {
				log.warn("Payout {} failed to auto-execute (CMI transfer error) — needs organizer manual override",
					payout.getId(), e);
				notifyOrganizer(payout, "failed to auto-execute");
			}
		}
		if (executed > 0 || notReady > 0) {
			log.info("Payout cron: executed {}, left {} pending for manual override", executed, notReady);
		}
	}

	private void notifyOrganizer(PayoutSchedule payout, String reason) {
		groupRepository.findById(payout.getGroupId()).ifPresent(group ->
			userRepository.findById(group.getOrganizerId()).ifPresent(organizer ->
				notificationProvider.notify(phoneNumberCodec.decrypt(organizer.getPhoneNumberEncrypted()),
					"Payout for cycle %d in \"%s\" %s — needs your manual review."
						.formatted(payout.getCycleNumber(), group.getName(), reason))));
	}

}
