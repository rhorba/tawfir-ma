package ma.tawfir.api.group;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
import ma.tawfir.api.group.entity.Group;
import ma.tawfir.api.group.entity.Frequency;
import ma.tawfir.api.group.entity.PayoutOrderMode;
import ma.tawfir.api.group.entity.PayoutSchedule;
import ma.tawfir.api.notification.NotificationProvider;
import ma.tawfir.api.user.UserRepository;
import ma.tawfir.api.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PayoutSchedulerTest {

	@Mock
	private PayoutScheduleRepository payoutScheduleRepository;
	@Mock
	private PayoutScheduleService payoutScheduleService;
	@Mock
	private GroupRepository groupRepository;
	@Mock
	private UserRepository userRepository;
	@Mock
	private PhoneNumberCodec phoneNumberCodec;
	@Mock
	private NotificationProvider notificationProvider;

	private PayoutScheduler scheduler;
	private UUID organizerId;

	@BeforeEach
	void setUp() {
		scheduler = new PayoutScheduler(payoutScheduleRepository, payoutScheduleService, groupRepository,
			userRepository, phoneNumberCodec, notificationProvider);

		organizerId = UUID.randomUUID();
		Group group = new Group("Daret", organizerId, BigDecimal.valueOf(500), Frequency.MONTHLY,
			(short) 2, PayoutOrderMode.RANDOMIZED);
		User organizer = new User("hashed-organizer-phone", "encrypted-organizer-phone");
		lenient().when(groupRepository.findById(any())).thenReturn(Optional.of(group));
		lenient().when(userRepository.findById(organizerId)).thenReturn(Optional.of(organizer));
		lenient().when(phoneNumberCodec.decrypt("encrypted-organizer-phone")).thenReturn("+212600000001");
	}

	private PayoutSchedule payout() {
		return new PayoutSchedule(UUID.randomUUID(), (short) 1, UUID.randomUUID(), LocalDate.now(), BigDecimal.valueOf(500));
	}

	@Test
	void executeDuePayouts_delegatesEachDuePayoutToService() {
		PayoutSchedule payoutA = payout();
		PayoutSchedule payoutB = payout();
		when(payoutScheduleRepository.findDuePending(any())).thenReturn(List.of(payoutA, payoutB));
		when(payoutScheduleService.executeIfReady(payoutA)).thenReturn(true);
		when(payoutScheduleService.executeIfReady(payoutB)).thenReturn(false);

		scheduler.executeDuePayouts();

		verify(payoutScheduleService, times(2)).executeIfReady(any());
	}

	@Test
	void executeDuePayouts_notReady_notifiesOrganizer() {
		PayoutSchedule payoutA = payout();
		when(payoutScheduleRepository.findDuePending(any())).thenReturn(List.of(payoutA));
		when(payoutScheduleService.executeIfReady(payoutA)).thenReturn(false);

		scheduler.executeDuePayouts();

		verify(notificationProvider).notify(eq("+212600000001"), any(String.class));
	}

	@Test
	void executeDuePayouts_onePayoutThrows_othersStillProcessedAndOrganizerNotified() {
		PayoutSchedule payoutA = payout();
		PayoutSchedule payoutB = payout();
		when(payoutScheduleRepository.findDuePending(any())).thenReturn(List.of(payoutA, payoutB));
		when(payoutScheduleService.executeIfReady(payoutA)).thenThrow(new RuntimeException("CMI down"));
		when(payoutScheduleService.executeIfReady(payoutB)).thenReturn(true);

		scheduler.executeDuePayouts();

		verify(payoutScheduleService).executeIfReady(payoutA);
		verify(payoutScheduleService).executeIfReady(payoutB);
		verify(notificationProvider, times(1)).notify(eq("+212600000001"), any(String.class));
	}

	@Test
	void executeDuePayouts_noneDue_noInteractionsWithService() {
		when(payoutScheduleRepository.findDuePending(any())).thenReturn(List.of());

		scheduler.executeDuePayouts();

		verify(payoutScheduleService, times(0)).executeIfReady(any());
		verify(notificationProvider, never()).notify(any(), any());
	}

}
