package ma.tawfir.api.group;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ma.tawfir.api.common.PhoneNumberCodec;
import ma.tawfir.api.group.entity.ContributionSchedule;
import ma.tawfir.api.group.entity.ContributionStatus;
import ma.tawfir.api.group.entity.Frequency;
import ma.tawfir.api.group.entity.Group;
import ma.tawfir.api.group.entity.PayoutOrderMode;
import ma.tawfir.api.notification.NotificationProvider;
import ma.tawfir.api.user.UserRepository;
import ma.tawfir.api.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LateContributionSchedulerTest {

	@Mock
	private ContributionScheduleRepository contributionScheduleRepository;
	@Mock
	private GroupRepository groupRepository;
	@Mock
	private UserRepository userRepository;
	@Mock
	private PhoneNumberCodec phoneNumberCodec;
	@Mock
	private NotificationProvider notificationProvider;

	private LateContributionScheduler scheduler;

	@BeforeEach
	void setUp() {
		scheduler = new LateContributionScheduler(
			contributionScheduleRepository, groupRepository, userRepository, phoneNumberCodec, notificationProvider);
	}

	@Test
	void flagLateContributions_noneOverdue_noFlipAndNoNotifications() {
		when(contributionScheduleRepository.findByStatusAndDueDateBefore(eq(ContributionStatus.PENDING), any()))
			.thenReturn(List.of());

		scheduler.flagLateContributions();

		verify(contributionScheduleRepository, never()).flagOverdueAsLate(any());
		verify(notificationProvider, never()).notify(any(), any());
	}

	@Test
	void flagLateContributions_overdueFound_flagsAndNotifiesMemberAndOrganizer() {
		UUID groupId = UUID.randomUUID();
		UUID memberId = UUID.randomUUID();
		UUID organizerId = UUID.randomUUID();
		ContributionSchedule schedule = new ContributionSchedule(groupId, (short) 3, memberId, LocalDate.now().minusDays(1));

		when(contributionScheduleRepository.findByStatusAndDueDateBefore(eq(ContributionStatus.PENDING), any()))
			.thenReturn(List.of(schedule));
		when(contributionScheduleRepository.flagOverdueAsLate(any())).thenReturn(1);

		Group group = new Group("Daret", organizerId, BigDecimal.valueOf(500), Frequency.MONTHLY,
			(short) 3, PayoutOrderMode.RANDOMIZED);
		when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));

		User member = new User("hashed-member-phone", "encrypted-member-phone");
		User organizer = new User("hashed-organizer-phone", "encrypted-organizer-phone");
		when(userRepository.findById(memberId)).thenReturn(Optional.of(member));
		when(userRepository.findById(organizerId)).thenReturn(Optional.of(organizer));
		when(phoneNumberCodec.decrypt("encrypted-member-phone")).thenReturn("+212600000002");
		when(phoneNumberCodec.decrypt("encrypted-organizer-phone")).thenReturn("+212600000001");

		scheduler.flagLateContributions();

		verify(contributionScheduleRepository).flagOverdueAsLate(any());
		verify(notificationProvider).notify(eq("+212600000002"), contains("Your contribution"));
		verify(notificationProvider).notify(eq("+212600000001"), contains("A member's contribution"));
		verify(notificationProvider, times(2)).notify(any(), any());
	}

}
