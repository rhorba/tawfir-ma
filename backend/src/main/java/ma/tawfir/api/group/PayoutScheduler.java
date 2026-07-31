package ma.tawfir.api.group;

import java.time.LocalDate;
import java.util.List;
import ma.tawfir.api.group.entity.PayoutSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Story 4.1: executes due payouts automatically. Each payout is its own
 * transaction (PayoutScheduleService#executeIfReady) so one CMI failure
 * doesn't roll back the others in the same run. Payouts left PENDING (not
 * ready) or FAILED (transfer error) surface no notification channel yet
 * (same accepted-TODO precedent as story 3.4) — an organizer sees the status
 * next time they load the group and can manually override.
 */
@Component
public class PayoutScheduler {

	private static final Logger log = LoggerFactory.getLogger(PayoutScheduler.class);

	private final PayoutScheduleRepository payoutScheduleRepository;
	private final PayoutScheduleService payoutScheduleService;

	public PayoutScheduler(PayoutScheduleRepository payoutScheduleRepository, PayoutScheduleService payoutScheduleService) {
		this.payoutScheduleRepository = payoutScheduleRepository;
		this.payoutScheduleService = payoutScheduleService;
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
				}
			} catch (RuntimeException e) {
				log.warn("Payout {} failed to auto-execute (CMI transfer error) — needs organizer manual override",
					payout.getId(), e);
			}
		}
		if (executed > 0 || notReady > 0) {
			log.info("Payout cron: executed {}, left {} pending for manual override", executed, notReady);
		}
	}

}
