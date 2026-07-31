package ma.tawfir.api.group;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ma.tawfir.api.group.entity.PayoutSchedule;
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

	private PayoutSchedule payout() {
		return new PayoutSchedule(UUID.randomUUID(), (short) 1, UUID.randomUUID(), LocalDate.now(), BigDecimal.valueOf(500));
	}

	@Test
	void executeDuePayouts_delegatesEachDuePayoutToService() {
		PayoutScheduler scheduler = new PayoutScheduler(payoutScheduleRepository, payoutScheduleService);
		PayoutSchedule payoutA = payout();
		PayoutSchedule payoutB = payout();
		when(payoutScheduleRepository.findDuePending(any())).thenReturn(List.of(payoutA, payoutB));
		when(payoutScheduleService.executeIfReady(payoutA)).thenReturn(true);
		when(payoutScheduleService.executeIfReady(payoutB)).thenReturn(false);

		scheduler.executeDuePayouts();

		verify(payoutScheduleService, times(2)).executeIfReady(any());
	}

	@Test
	void executeDuePayouts_onePayoutThrows_othersStillProcessed() {
		PayoutScheduler scheduler = new PayoutScheduler(payoutScheduleRepository, payoutScheduleService);
		PayoutSchedule payoutA = payout();
		PayoutSchedule payoutB = payout();
		when(payoutScheduleRepository.findDuePending(any())).thenReturn(List.of(payoutA, payoutB));
		when(payoutScheduleService.executeIfReady(payoutA)).thenThrow(new RuntimeException("CMI down"));
		when(payoutScheduleService.executeIfReady(payoutB)).thenReturn(true);

		scheduler.executeDuePayouts();

		verify(payoutScheduleService).executeIfReady(payoutA);
		verify(payoutScheduleService).executeIfReady(payoutB);
	}

	@Test
	void executeDuePayouts_noneDue_noInteractionsWithService() {
		PayoutScheduler scheduler = new PayoutScheduler(payoutScheduleRepository, payoutScheduleService);
		when(payoutScheduleRepository.findDuePending(any())).thenReturn(List.of());

		scheduler.executeDuePayouts();

		verify(payoutScheduleService, times(0)).executeIfReady(any());
	}

}
