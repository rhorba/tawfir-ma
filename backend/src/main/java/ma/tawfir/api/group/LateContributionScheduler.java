package ma.tawfir.api.group;

import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Story 3.4: flips PENDING contributions past their due_date to LATE. Status
 * flip only — no notification delivery (SMS/push/email channel is undecided;
 * see decisions.md 2026-07-27). Members/Organizers see the LATE status next
 * time they load the contributions list.
 */
@Component
public class LateContributionScheduler {

	private static final Logger log = LoggerFactory.getLogger(LateContributionScheduler.class);

	private final ContributionScheduleRepository contributionScheduleRepository;

	public LateContributionScheduler(ContributionScheduleRepository contributionScheduleRepository) {
		this.contributionScheduleRepository = contributionScheduleRepository;
	}

	@Scheduled(cron = "0 0 * * * *")
	@Transactional
	public void flagLateContributions() {
		int flagged = contributionScheduleRepository.flagOverdueAsLate(LocalDate.now());
		if (flagged > 0) {
			log.info("Flagged {} overdue contribution(s) as LATE", flagged);
		}
	}

}
