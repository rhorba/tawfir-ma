package ma.tawfir.api.savings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ma.tawfir.api.dispute.DisputeRepository;
import ma.tawfir.api.dispute.entity.Dispute;
import ma.tawfir.api.group.ContributionScheduleRepository;
import ma.tawfir.api.group.GroupMembershipRepository;
import ma.tawfir.api.group.entity.ContributionSchedule;
import ma.tawfir.api.group.entity.ContributionStatus;
import ma.tawfir.api.group.entity.GroupMembership;
import ma.tawfir.api.group.entity.MembershipRole;
import ma.tawfir.api.ledger.LedgerEntryRepository;
import ma.tawfir.api.ledger.entity.LedgerEntry;
import ma.tawfir.api.ledger.entity.LedgerSource;
import ma.tawfir.api.savings.dto.SavingsHistoryResponse;
import ma.tawfir.api.savings.entity.SavingsHistorySnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SavingsHistoryServiceTest {

	@Mock
	private SavingsHistorySnapshotRepository snapshotRepository;
	@Mock
	private ContributionScheduleRepository contributionScheduleRepository;
	@Mock
	private GroupMembershipRepository membershipRepository;
	@Mock
	private LedgerEntryRepository ledgerEntryRepository;
	@Mock
	private DisputeRepository disputeRepository;

	private SavingsHistoryService savingsHistoryService;

	@BeforeEach
	void setUp() {
		savingsHistoryService = new SavingsHistoryService(
			snapshotRepository, contributionScheduleRepository, membershipRepository, ledgerEntryRepository, disputeRepository);
	}

	private ContributionSchedule schedule(UUID groupId, UUID id, short cycle, UUID userId, ContributionStatus status,
			boolean wasLate) {
		ContributionSchedule schedule = new ContributionSchedule(groupId, cycle, userId, LocalDate.now());
		ReflectionTestUtils.setField(schedule, "id", id);
		ReflectionTestUtils.setField(schedule, "status", status);
		ReflectionTestUtils.setField(schedule, "wasLate", wasLate);
		return schedule;
	}

	@Test
	void recordSnapshotsIfCycleComplete_notAllConfirmed_doesNothing() {
		UUID groupId = UUID.randomUUID();
		UUID userA = UUID.randomUUID();
		UUID userB = UUID.randomUUID();
		when(contributionScheduleRepository.findByGroupIdOrderByCycleNumberAscUserIdAsc(groupId)).thenReturn(List.of(
			schedule(groupId, UUID.randomUUID(), (short) 1, userA, ContributionStatus.CONFIRMED, false),
			schedule(groupId, UUID.randomUUID(), (short) 1, userB, ContributionStatus.MARKED_PAID, false)));

		savingsHistoryService.recordSnapshotsIfCycleComplete(groupId, (short) 1);

		verify(snapshotRepository, never()).save(any());
		verify(membershipRepository, never()).findByGroupId(any());
	}

	@Test
	void recordSnapshotsIfCycleComplete_allConfirmed_insertsOneSnapshotPerMember() {
		UUID groupId = UUID.randomUUID();
		UUID userA = UUID.randomUUID();
		UUID userB = UUID.randomUUID();
		when(contributionScheduleRepository.findByGroupIdOrderByCycleNumberAscUserIdAsc(groupId)).thenReturn(List.of(
			schedule(groupId, UUID.randomUUID(), (short) 1, userA, ContributionStatus.CONFIRMED, false),
			schedule(groupId, UUID.randomUUID(), (short) 1, userB, ContributionStatus.CONFIRMED, false)));
		when(membershipRepository.findByGroupId(groupId)).thenReturn(List.of(
			new GroupMembership(groupId, userA, MembershipRole.ORGANIZER, (short) 1),
			new GroupMembership(groupId, userB, MembershipRole.MEMBER, (short) 2)));
		when(ledgerEntryRepository.findByGroupIdOrderByCreatedAtAsc(groupId)).thenReturn(List.of());
		when(disputeRepository.findByGroupId(groupId)).thenReturn(List.of());

		savingsHistoryService.recordSnapshotsIfCycleComplete(groupId, (short) 1);

		verify(snapshotRepository, times(2)).save(any(SavingsHistorySnapshot.class));
	}

	@Test
	void recordSnapshotsIfCycleComplete_onTimeRateExcludesWasLateContributions() {
		UUID groupId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID cycle1Id = UUID.randomUUID();
		UUID cycle2Id = UUID.randomUUID();
		// User has 2 confirmed contributions across two cycles: one on time, one was late.
		when(contributionScheduleRepository.findByGroupIdOrderByCycleNumberAscUserIdAsc(groupId)).thenReturn(List.of(
			schedule(groupId, cycle1Id, (short) 1, userId, ContributionStatus.CONFIRMED, false),
			schedule(groupId, cycle2Id, (short) 2, userId, ContributionStatus.CONFIRMED, true)));
		when(membershipRepository.findByGroupId(groupId))
			.thenReturn(List.of(new GroupMembership(groupId, userId, MembershipRole.ORGANIZER, (short) 1)));
		when(ledgerEntryRepository.findByGroupIdOrderByCreatedAtAsc(groupId)).thenReturn(List.of());
		when(disputeRepository.findByGroupId(groupId)).thenReturn(List.of());

		savingsHistoryService.recordSnapshotsIfCycleComplete(groupId, (short) 2);

		ArgumentCaptor<SavingsHistorySnapshot> captor = ArgumentCaptor.forClass(SavingsHistorySnapshot.class);
		verify(snapshotRepository).save(captor.capture());
		SavingsHistorySnapshot snapshot = captor.getValue();
		assertThat(snapshot.getCyclesCompleted()).isEqualTo((short) 2);
		assertThat(snapshot.getOnTimeRate()).isEqualByComparingTo(BigDecimal.valueOf(50.0).setScale(2));
		assertThat(snapshot.getDisputesInvolved()).isZero();
	}

	@Test
	void recordSnapshotsIfCycleComplete_countsDisputesByContributionPayer() {
		UUID groupId = UUID.randomUUID();
		UUID payerId = UUID.randomUUID();
		UUID scheduleId = UUID.randomUUID();
		UUID ledgerEntryId = UUID.randomUUID();
		when(contributionScheduleRepository.findByGroupIdOrderByCycleNumberAscUserIdAsc(groupId))
			.thenReturn(List.of(schedule(groupId, scheduleId, (short) 1, payerId, ContributionStatus.CONFIRMED, false)));
		when(membershipRepository.findByGroupId(groupId))
			.thenReturn(List.of(new GroupMembership(groupId, payerId, MembershipRole.ORGANIZER, (short) 1)));
		LedgerEntry ledgerEntry = LedgerEntry.contribution(groupId, scheduleId, payerId, BigDecimal.TEN, LedgerSource.ORGANIZER_CONFIRMED);
		ReflectionTestUtils.setField(ledgerEntry, "id", ledgerEntryId);
		when(ledgerEntryRepository.findByGroupIdOrderByCreatedAtAsc(groupId)).thenReturn(List.of(ledgerEntry));
		Dispute dispute = new Dispute(groupId, ledgerEntryId, UUID.randomUUID(), "reason", null);
		when(disputeRepository.findByGroupId(groupId)).thenReturn(List.of(dispute));

		savingsHistoryService.recordSnapshotsIfCycleComplete(groupId, (short) 1);

		ArgumentCaptor<SavingsHistorySnapshot> captor = ArgumentCaptor.forClass(SavingsHistorySnapshot.class);
		verify(snapshotRepository).save(captor.capture());
		assertThat(captor.getValue().getDisputesInvolved()).isEqualTo((short) 1);
	}

	@Test
	void getHistoryForUser_mapsSnapshotsToResponses() {
		UUID userId = UUID.randomUUID();
		UUID groupId = UUID.randomUUID();
		SavingsHistorySnapshot snapshot =
			new SavingsHistorySnapshot(userId, groupId, (short) 3, BigDecimal.valueOf(100.0).setScale(2), (short) 0);
		when(snapshotRepository.findByUserIdOrderByComputedAtDesc(userId)).thenReturn(List.of(snapshot));

		List<SavingsHistoryResponse> result = savingsHistoryService.getHistoryForUser(userId);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).groupId()).isEqualTo(groupId);
		assertThat(result.get(0).cyclesCompleted()).isEqualTo((short) 3);
	}

}
