package ma.tawfir.api.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ma.tawfir.api.common.ForbiddenException;
import ma.tawfir.api.common.NotFoundException;
import ma.tawfir.api.common.ValidationException;
import ma.tawfir.api.group.dto.PayoutResponse;
import ma.tawfir.api.group.entity.GroupMembership;
import ma.tawfir.api.group.entity.MembershipRole;
import ma.tawfir.api.group.entity.PayoutSchedule;
import ma.tawfir.api.group.entity.PayoutStatus;
import ma.tawfir.api.ledger.LedgerEntryRepository;
import ma.tawfir.api.ledger.entity.LedgerEntry;
import ma.tawfir.api.payment.CmiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PayoutScheduleServiceTest {

	@Mock
	private PayoutScheduleRepository payoutScheduleRepository;
	@Mock
	private ContributionScheduleRepository contributionScheduleRepository;
	@Mock
	private GroupMembershipRepository membershipRepository;
	@Mock
	private LedgerEntryRepository ledgerEntryRepository;
	@Mock
	private CmiClient cmiClient;

	private PayoutScheduleService payoutScheduleService;

	@BeforeEach
	void setUp() {
		payoutScheduleService = new PayoutScheduleService(payoutScheduleRepository, contributionScheduleRepository,
			membershipRepository, ledgerEntryRepository, cmiClient);
	}

	private PayoutSchedule payout(UUID id, UUID groupId, UUID recipientId, PayoutStatus status) {
		PayoutSchedule payout = new PayoutSchedule(groupId, (short) 1, recipientId, LocalDate.now(), BigDecimal.valueOf(500));
		ReflectionTestUtils.setField(payout, "id", id);
		ReflectionTestUtils.setField(payout, "status", status);
		return payout;
	}

	@Test
	void executeIfReady_allContributionsConfirmed_executesAndAppendsLedgerEntry() {
		UUID groupId = UUID.randomUUID();
		UUID payoutId = UUID.randomUUID();
		UUID recipientId = UUID.randomUUID();
		PayoutSchedule payout = payout(payoutId, groupId, recipientId, PayoutStatus.PENDING);
		when(contributionScheduleRepository.countByGroupIdAndCycleNumberAndStatusNot(eq(groupId), eq((short) 1), any()))
			.thenReturn(0L);
		when(payoutScheduleRepository.findById(payoutId)).thenReturn(Optional.of(payout));
		when(payoutScheduleRepository.compareAndSetStatus(eq(payoutId), anySet(), eq(PayoutStatus.EXECUTED))).thenReturn(1);

		boolean executed = payoutScheduleService.executeIfReady(payout);

		assertThat(executed).isTrue();
		verify(cmiClient).initiateTransfer(payoutId.toString(), BigDecimal.valueOf(500));
		verify(ledgerEntryRepository).save(any(LedgerEntry.class));
	}

	@Test
	void executeIfReady_someContributionUnconfirmed_staysPendingNoTransfer() {
		UUID groupId = UUID.randomUUID();
		UUID payoutId = UUID.randomUUID();
		PayoutSchedule payout = payout(payoutId, groupId, UUID.randomUUID(), PayoutStatus.PENDING);
		when(contributionScheduleRepository.countByGroupIdAndCycleNumberAndStatusNot(eq(groupId), eq((short) 1), any()))
			.thenReturn(1L);

		boolean executed = payoutScheduleService.executeIfReady(payout);

		assertThat(executed).isFalse();
		verify(cmiClient, never()).initiateTransfer(any(), any());
		verify(ledgerEntryRepository, never()).save(any());
	}

	@Test
	void executeIfReady_cmiTransferThrows_marksFailedAndPropagates() {
		UUID groupId = UUID.randomUUID();
		UUID payoutId = UUID.randomUUID();
		PayoutSchedule payout = payout(payoutId, groupId, UUID.randomUUID(), PayoutStatus.PENDING);
		when(contributionScheduleRepository.countByGroupIdAndCycleNumberAndStatusNot(eq(groupId), eq((short) 1), any()))
			.thenReturn(0L);
		when(payoutScheduleRepository.findById(payoutId)).thenReturn(Optional.of(payout));
		when(cmiClient.initiateTransfer(any(), any())).thenThrow(new RuntimeException("CMI down"));

		assertThatThrownBy(() -> payoutScheduleService.executeIfReady(payout)).isInstanceOf(RuntimeException.class);

		verify(payoutScheduleRepository).compareAndSetStatus(eq(payoutId), anySet(), eq(PayoutStatus.FAILED));
		verify(ledgerEntryRepository, never()).save(any());
	}

	@Test
	void executeManualOverride_organizer_transitionsToManualOverride() {
		UUID groupId = UUID.randomUUID();
		UUID payoutId = UUID.randomUUID();
		UUID organizerId = UUID.randomUUID();
		PayoutSchedule payout = payout(payoutId, groupId, UUID.randomUUID(), PayoutStatus.PENDING);
		when(membershipRepository.findByGroupIdAndUserId(groupId, organizerId))
			.thenReturn(Optional.of(new GroupMembership(groupId, organizerId, MembershipRole.ORGANIZER, (short) 1)));
		when(payoutScheduleRepository.findById(payoutId)).thenReturn(Optional.of(payout));
		when(payoutScheduleRepository.compareAndSetStatus(eq(payoutId), anySet(), eq(PayoutStatus.MANUAL_OVERRIDE))).thenReturn(1);

		PayoutResponse response = payoutScheduleService.executeManualOverride(organizerId, groupId, payoutId);

		assertThat(response.id()).isEqualTo(payoutId);
		verify(cmiClient).initiateTransfer(payoutId.toString(), BigDecimal.valueOf(500));
		verify(ledgerEntryRepository).save(any(LedgerEntry.class));
	}

	@Test
	void executeManualOverride_nonOrganizer_throwsForbiddenWithoutTransfer() {
		UUID groupId = UUID.randomUUID();
		UUID payoutId = UUID.randomUUID();
		UUID memberId = UUID.randomUUID();
		when(membershipRepository.findByGroupIdAndUserId(groupId, memberId))
			.thenReturn(Optional.of(new GroupMembership(groupId, memberId, MembershipRole.MEMBER, (short) 1)));

		assertThatThrownBy(() -> payoutScheduleService.executeManualOverride(memberId, groupId, payoutId))
			.isInstanceOf(ForbiddenException.class);
		verify(cmiClient, never()).initiateTransfer(any(), any());
	}

	@Test
	void executeManualOverride_payoutFromDifferentGroup_throwsNotFound() {
		UUID groupId = UUID.randomUUID();
		UUID otherGroupId = UUID.randomUUID();
		UUID payoutId = UUID.randomUUID();
		UUID organizerId = UUID.randomUUID();
		when(membershipRepository.findByGroupIdAndUserId(groupId, organizerId))
			.thenReturn(Optional.of(new GroupMembership(groupId, organizerId, MembershipRole.ORGANIZER, (short) 1)));
		when(payoutScheduleRepository.findById(payoutId))
			.thenReturn(Optional.of(payout(payoutId, otherGroupId, UUID.randomUUID(), PayoutStatus.PENDING)));

		assertThatThrownBy(() -> payoutScheduleService.executeManualOverride(organizerId, groupId, payoutId))
			.isInstanceOf(NotFoundException.class);
		verify(cmiClient, never()).initiateTransfer(any(), any());
	}

	@Test
	void confirmViaWebhook_stillPending_drivesTransitionToExecuted() {
		UUID groupId = UUID.randomUUID();
		UUID payoutId = UUID.randomUUID();
		UUID recipientId = UUID.randomUUID();
		PayoutSchedule payout = payout(payoutId, groupId, recipientId, PayoutStatus.PENDING);
		when(payoutScheduleRepository.findById(payoutId)).thenReturn(Optional.of(payout));
		when(payoutScheduleRepository.compareAndSetStatus(eq(payoutId), anySet(), eq(PayoutStatus.EXECUTED))).thenReturn(1);

		payoutScheduleService.confirmViaWebhook(payoutId, BigDecimal.valueOf(500));

		verify(cmiClient).initiateTransfer(payoutId.toString(), BigDecimal.valueOf(500));
		verify(ledgerEntryRepository).save(any(LedgerEntry.class));
	}

	@Test
	void confirmViaWebhook_alreadyExecuted_isIdempotentNoOp() {
		UUID groupId = UUID.randomUUID();
		UUID payoutId = UUID.randomUUID();
		PayoutSchedule payout = payout(payoutId, groupId, UUID.randomUUID(), PayoutStatus.EXECUTED);
		when(payoutScheduleRepository.findById(payoutId)).thenReturn(Optional.of(payout));

		payoutScheduleService.confirmViaWebhook(payoutId, BigDecimal.valueOf(500));

		verify(cmiClient, never()).initiateTransfer(any(), any());
		verify(ledgerEntryRepository, never()).save(any());
	}

	@Test
	void confirmViaWebhook_alreadyManualOverride_isIdempotentNoOp() {
		UUID groupId = UUID.randomUUID();
		UUID payoutId = UUID.randomUUID();
		PayoutSchedule payout = payout(payoutId, groupId, UUID.randomUUID(), PayoutStatus.MANUAL_OVERRIDE);
		when(payoutScheduleRepository.findById(payoutId)).thenReturn(Optional.of(payout));

		payoutScheduleService.confirmViaWebhook(payoutId, BigDecimal.valueOf(500));

		verify(cmiClient, never()).initiateTransfer(any(), any());
	}

	@Test
	void confirmViaWebhook_amountMismatch_throwsValidationWithoutTransfer() {
		UUID groupId = UUID.randomUUID();
		UUID payoutId = UUID.randomUUID();
		PayoutSchedule payout = payout(payoutId, groupId, UUID.randomUUID(), PayoutStatus.PENDING);
		when(payoutScheduleRepository.findById(payoutId)).thenReturn(Optional.of(payout));

		assertThatThrownBy(() -> payoutScheduleService.confirmViaWebhook(payoutId, BigDecimal.valueOf(999)))
			.isInstanceOf(ValidationException.class);
		verify(cmiClient, never()).initiateTransfer(any(), any());
	}

	@Test
	void listForGroup_member_returnsPayouts() {
		UUID groupId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		when(membershipRepository.existsByGroupIdAndUserId(groupId, userId)).thenReturn(true);
		when(payoutScheduleRepository.findByGroupIdOrderByCycleNumberAsc(groupId))
			.thenReturn(List.of(payout(UUID.randomUUID(), groupId, userId, PayoutStatus.PENDING)));

		assertThat(payoutScheduleService.listForGroup(userId, groupId, false)).hasSize(1);
	}

	@Test
	void listForGroup_nonMember_throwsForbidden() {
		UUID groupId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		when(membershipRepository.existsByGroupIdAndUserId(groupId, userId)).thenReturn(false);

		assertThatThrownBy(() -> payoutScheduleService.listForGroup(userId, groupId, false))
			.isInstanceOf(ForbiddenException.class);
	}

	@Test
	void listForGroup_admin_bypassesMembershipCheck() {
		UUID groupId = UUID.randomUUID();
		UUID adminId = UUID.randomUUID();
		when(payoutScheduleRepository.findByGroupIdOrderByCycleNumberAsc(groupId)).thenReturn(List.of());

		payoutScheduleService.listForGroup(adminId, groupId, true);

		verify(membershipRepository, never()).existsByGroupIdAndUserId(any(), any());
	}

}
