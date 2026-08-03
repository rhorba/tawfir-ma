package ma.tawfir.api.group;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ma.tawfir.api.common.PhoneNumberCodec;
import ma.tawfir.api.group.entity.ContributionSchedule;
import ma.tawfir.api.group.entity.ContributionStatus;
import ma.tawfir.api.notification.NotificationProvider;
import ma.tawfir.api.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Story 3.4/FR-5: flips PENDING contributions past their due_date to LATE and
 * notifies both the member and the group's organizer (NotificationProvider —
 * mocked until a real SMS/push/email provider is chosen, security-tawfir.md §3).
 */
@Component
public class LateContributionScheduler {

	private static final Logger log = LoggerFactory.getLogger(LateContributionScheduler.class);

	private final ContributionScheduleRepository contributionScheduleRepository;
	private final GroupRepository groupRepository;
	private final UserRepository userRepository;
	private final PhoneNumberCodec phoneNumberCodec;
	private final NotificationProvider notificationProvider;

	public LateContributionScheduler(ContributionScheduleRepository contributionScheduleRepository,
			GroupRepository groupRepository, UserRepository userRepository, PhoneNumberCodec phoneNumberCodec,
			NotificationProvider notificationProvider) {
		this.contributionScheduleRepository = contributionScheduleRepository;
		this.groupRepository = groupRepository;
		this.userRepository = userRepository;
		this.phoneNumberCodec = phoneNumberCodec;
		this.notificationProvider = notificationProvider;
	}

	@Scheduled(cron = "0 0 * * * *")
	@Transactional
	public void flagLateContributions() {
		LocalDate today = LocalDate.now();
		List<ContributionSchedule> overdue =
			contributionScheduleRepository.findByStatusAndDueDateBefore(ContributionStatus.PENDING, today);
		if (overdue.isEmpty()) {
			return;
		}

		int flagged = contributionScheduleRepository.flagOverdueAsLate(today);
		overdue.forEach(this::notifyLate);
		log.info("Flagged {} overdue contribution(s) as LATE", flagged);
	}

	private void notifyLate(ContributionSchedule schedule) {
		groupRepository.findById(schedule.getGroupId()).ifPresent(group -> {
			short cycle = schedule.getCycleNumber();
			String groupName = group.getName();
			notifyUser(schedule.getUserId(),
				"Your contribution for cycle %d in \"%s\" is now late.".formatted(cycle, groupName));
			notifyUser(group.getOrganizerId(),
				"A member's contribution for cycle %d in \"%s\" is now late.".formatted(cycle, groupName));
		});
	}

	private void notifyUser(UUID userId, String message) {
		userRepository.findById(userId).ifPresent(user ->
			notificationProvider.notify(phoneNumberCodec.decrypt(user.getPhoneNumberEncrypted()), message));
	}

}
