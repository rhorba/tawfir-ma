package ma.tawfir.api.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
import ma.tawfir.api.group.dto.ContributionResponse;
import ma.tawfir.api.group.entity.ContributionSchedule;
import ma.tawfir.api.group.entity.ContributionStatus;
import ma.tawfir.api.group.entity.Frequency;
import ma.tawfir.api.group.entity.Group;
import ma.tawfir.api.group.entity.GroupMembership;
import ma.tawfir.api.group.entity.MembershipRole;
import ma.tawfir.api.group.entity.PayoutOrderMode;
import ma.tawfir.api.ledger.LedgerEntryRepository;
import ma.tawfir.api.ledger.entity.LedgerEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ContributionServiceTest {

	@Mock
	private ContributionScheduleRepository contributionScheduleRepository;
	@Mock
	private GroupMembershipRepository membershipRepository;
	@Mock
	private GroupRepository groupRepository;
	@Mock
	private LedgerEntryRepository ledgerEntryRepository;

	private ContributionService contributionService;

	@BeforeEach
	void setUp() {
		contributionService = new ContributionService(contributionScheduleRepository, membershipRepository,
			groupRepository, ledgerEntryRepository);
	}

	private ContributionSchedule schedule(UUID groupId, UUID id, UUID userId, ContributionStatus status) {
		ContributionSchedule schedule = new ContributionSchedule(groupId, (short) 1, userId, LocalDate.now());
		ReflectionTestUtils.setField(schedule, "id", id);
		ReflectionTestUtils.setField(schedule, "status", status);
		return schedule;
	}

	@Test
	void markPaid_ownerWithPendingSchedule_transitionsToMarkedPaid() {
		UUID groupId = UUID.randomUUID();
		UUID scheduleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		when(contributionScheduleRepository.findById(scheduleId))
			.thenReturn(Optional.of(schedule(groupId, scheduleId, userId, ContributionStatus.PENDING)))
			.thenReturn(Optional.of(schedule(groupId, scheduleId, userId, ContributionStatus.MARKED_PAID)));
		when(contributionScheduleRepository.compareAndSetStatus(eq(scheduleId), anySet(), eq(ContributionStatus.MARKED_PAID)))
			.thenReturn(1);

		ContributionResponse response = contributionService.markPaid(userId, groupId, scheduleId);

		assertThat(response.status()).isEqualTo(ContributionStatus.MARKED_PAID);
	}

	@Test
	void markPaid_notOwner_throwsForbidden() {
		UUID groupId = UUID.randomUUID();
		UUID scheduleId = UUID.randomUUID();
		UUID ownerId = UUID.randomUUID();
		UUID otherUserId = UUID.randomUUID();
		when(contributionScheduleRepository.findById(scheduleId))
			.thenReturn(Optional.of(schedule(groupId, scheduleId, ownerId, ContributionStatus.PENDING)));

		assertThatThrownBy(() -> contributionService.markPaid(otherUserId, groupId, scheduleId))
			.isInstanceOf(ForbiddenException.class);
		verify(contributionScheduleRepository, never()).compareAndSetStatus(any(), any(), any());
	}

	@Test
	void markPaid_scheduleFromDifferentGroup_throwsNotFound() {
		UUID groupId = UUID.randomUUID();
		UUID otherGroupId = UUID.randomUUID();
		UUID scheduleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		when(contributionScheduleRepository.findById(scheduleId))
			.thenReturn(Optional.of(schedule(otherGroupId, scheduleId, userId, ContributionStatus.PENDING)));

		assertThatThrownBy(() -> contributionService.markPaid(userId, groupId, scheduleId))
			.isInstanceOf(NotFoundException.class);
	}

	@Test
	void markPaid_concurrentDoubleMark_secondCallerGetsValidationError() {
		UUID groupId = UUID.randomUUID();
		UUID scheduleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		when(contributionScheduleRepository.findById(scheduleId))
			.thenReturn(Optional.of(schedule(groupId, scheduleId, userId, ContributionStatus.PENDING)));
		when(contributionScheduleRepository.compareAndSetStatus(eq(scheduleId), anySet(), eq(ContributionStatus.MARKED_PAID)))
			.thenReturn(0);

		assertThatThrownBy(() -> contributionService.markPaid(userId, groupId, scheduleId))
			.isInstanceOf(ValidationException.class);
	}

	@Test
	void confirm_organizer_transitionsAndAppendsLedgerEntry() {
		UUID groupId = UUID.randomUUID();
		UUID scheduleId = UUID.randomUUID();
		UUID memberId = UUID.randomUUID();
		UUID organizerId = UUID.randomUUID();
		when(membershipRepository.findByGroupIdAndUserId(groupId, organizerId))
			.thenReturn(Optional.of(new GroupMembership(groupId, organizerId, MembershipRole.ORGANIZER, (short) 1)));
		when(contributionScheduleRepository.findById(scheduleId))
			.thenReturn(Optional.of(schedule(groupId, scheduleId, memberId, ContributionStatus.MARKED_PAID)))
			.thenReturn(Optional.of(schedule(groupId, scheduleId, memberId, ContributionStatus.CONFIRMED)));
		when(contributionScheduleRepository.compareAndSetStatus(eq(scheduleId), anySet(), eq(ContributionStatus.CONFIRMED)))
			.thenReturn(1);
		Group group = new Group("Daret", organizerId, BigDecimal.valueOf(500), Frequency.MONTHLY, (short) 1, PayoutOrderMode.MANUAL);
		when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));

		ContributionResponse response = contributionService.confirm(organizerId, groupId, scheduleId);

		assertThat(response.status()).isEqualTo(ContributionStatus.CONFIRMED);
		verify(ledgerEntryRepository).save(any(LedgerEntry.class));
	}

	@Test
	void confirm_nonOrganizerMember_throwsForbiddenWithoutTouchingSchedule() {
		UUID groupId = UUID.randomUUID();
		UUID scheduleId = UUID.randomUUID();
		UUID memberId = UUID.randomUUID();
		when(membershipRepository.findByGroupIdAndUserId(groupId, memberId))
			.thenReturn(Optional.of(new GroupMembership(groupId, memberId, MembershipRole.MEMBER, (short) 1)));

		assertThatThrownBy(() -> contributionService.confirm(memberId, groupId, scheduleId))
			.isInstanceOf(ForbiddenException.class);
		verify(contributionScheduleRepository, never()).findById(any());
		verify(ledgerEntryRepository, never()).save(any());
	}

	@Test
	void confirm_notYetMarkedPaid_throwsValidationWithoutAppendingLedgerEntry() {
		UUID groupId = UUID.randomUUID();
		UUID scheduleId = UUID.randomUUID();
		UUID memberId = UUID.randomUUID();
		UUID organizerId = UUID.randomUUID();
		when(membershipRepository.findByGroupIdAndUserId(groupId, organizerId))
			.thenReturn(Optional.of(new GroupMembership(groupId, organizerId, MembershipRole.ORGANIZER, (short) 1)));
		when(contributionScheduleRepository.findById(scheduleId))
			.thenReturn(Optional.of(schedule(groupId, scheduleId, memberId, ContributionStatus.PENDING)));
		when(contributionScheduleRepository.compareAndSetStatus(eq(scheduleId), anySet(), eq(ContributionStatus.CONFIRMED)))
			.thenReturn(0);

		assertThatThrownBy(() -> contributionService.confirm(organizerId, groupId, scheduleId))
			.isInstanceOf(ValidationException.class);
		verify(ledgerEntryRepository, never()).save(any());
	}

	@Test
	void listForGroup_member_returnsSchedules() {
		UUID groupId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		lenient().when(membershipRepository.existsByGroupIdAndUserId(groupId, userId)).thenReturn(true);
		when(contributionScheduleRepository.findByGroupIdOrderByCycleNumberAscUserIdAsc(groupId))
			.thenReturn(List.of(schedule(groupId, UUID.randomUUID(), userId, ContributionStatus.PENDING)));

		List<ContributionResponse> result = contributionService.listForGroup(userId, groupId, false);

		assertThat(result).hasSize(1);
	}

	@Test
	void listForGroup_nonMember_throwsForbidden() {
		UUID groupId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		when(membershipRepository.existsByGroupIdAndUserId(groupId, userId)).thenReturn(false);

		assertThatThrownBy(() -> contributionService.listForGroup(userId, groupId, false))
			.isInstanceOf(ForbiddenException.class);
	}

	@Test
	void listForGroup_admin_bypassesMembershipCheck() {
		UUID groupId = UUID.randomUUID();
		UUID adminId = UUID.randomUUID();
		when(contributionScheduleRepository.findByGroupIdOrderByCycleNumberAscUserIdAsc(groupId)).thenReturn(List.of());

		contributionService.listForGroup(adminId, groupId, true);

		verify(membershipRepository, never()).existsByGroupIdAndUserId(any(), any());
	}

	@Test
	void confirmViaWebhook_pendingContribution_confirmsWithoutRequiringMarkedPaidFirst() {
		UUID groupId = UUID.randomUUID();
		UUID scheduleId = UUID.randomUUID();
		UUID payerId = UUID.randomUUID();
		when(contributionScheduleRepository.findById(scheduleId))
			.thenReturn(Optional.of(schedule(groupId, scheduleId, payerId, ContributionStatus.PENDING)))
			.thenReturn(Optional.of(schedule(groupId, scheduleId, payerId, ContributionStatus.CONFIRMED)));
		Group group = new Group("Daret", payerId, BigDecimal.valueOf(200), Frequency.MONTHLY, (short) 1, PayoutOrderMode.MANUAL);
		when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
		when(contributionScheduleRepository.compareAndSetStatus(eq(scheduleId), anySet(), eq(ContributionStatus.CONFIRMED)))
			.thenReturn(1);

		ContributionResponse response = contributionService.confirmViaWebhook(scheduleId, BigDecimal.valueOf(200));

		assertThat(response.status()).isEqualTo(ContributionStatus.CONFIRMED);
		verify(ledgerEntryRepository).save(any(LedgerEntry.class));
	}

	@Test
	void confirmViaWebhook_alreadyConfirmed_isIdempotentNoOp() {
		UUID groupId = UUID.randomUUID();
		UUID scheduleId = UUID.randomUUID();
		UUID payerId = UUID.randomUUID();
		when(contributionScheduleRepository.findById(scheduleId))
			.thenReturn(Optional.of(schedule(groupId, scheduleId, payerId, ContributionStatus.CONFIRMED)));

		ContributionResponse response = contributionService.confirmViaWebhook(scheduleId, BigDecimal.valueOf(200));

		assertThat(response.status()).isEqualTo(ContributionStatus.CONFIRMED);
		verify(ledgerEntryRepository, never()).save(any());
	}

	@Test
	void confirmViaWebhook_amountMismatch_throwsValidationWithoutAppendingLedgerEntry() {
		UUID groupId = UUID.randomUUID();
		UUID scheduleId = UUID.randomUUID();
		UUID payerId = UUID.randomUUID();
		when(contributionScheduleRepository.findById(scheduleId))
			.thenReturn(Optional.of(schedule(groupId, scheduleId, payerId, ContributionStatus.PENDING)));
		Group group = new Group("Daret", payerId, BigDecimal.valueOf(200), Frequency.MONTHLY, (short) 1, PayoutOrderMode.MANUAL);
		when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));

		assertThatThrownBy(() -> contributionService.confirmViaWebhook(scheduleId, BigDecimal.valueOf(999)))
			.isInstanceOf(ValidationException.class);
		verify(contributionScheduleRepository, never()).compareAndSetStatus(any(), any(), any());
		verify(ledgerEntryRepository, never()).save(any());
	}

	@Test
	void confirmViaWebhook_racedAgainstAnotherConfirm_returnsAlreadyConfirmedWithoutError() {
		UUID groupId = UUID.randomUUID();
		UUID scheduleId = UUID.randomUUID();
		UUID payerId = UUID.randomUUID();
		when(contributionScheduleRepository.findById(scheduleId))
			.thenReturn(Optional.of(schedule(groupId, scheduleId, payerId, ContributionStatus.MARKED_PAID)))
			.thenReturn(Optional.of(schedule(groupId, scheduleId, payerId, ContributionStatus.CONFIRMED)));
		Group group = new Group("Daret", payerId, BigDecimal.valueOf(200), Frequency.MONTHLY, (short) 1, PayoutOrderMode.MANUAL);
		when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
		when(contributionScheduleRepository.compareAndSetStatus(eq(scheduleId), anySet(), eq(ContributionStatus.CONFIRMED)))
			.thenReturn(0);

		ContributionResponse response = contributionService.confirmViaWebhook(scheduleId, BigDecimal.valueOf(200));

		assertThat(response.status()).isEqualTo(ContributionStatus.CONFIRMED);
		verify(ledgerEntryRepository, never()).save(any());
	}

}
