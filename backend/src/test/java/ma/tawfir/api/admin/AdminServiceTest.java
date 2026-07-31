package ma.tawfir.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ma.tawfir.api.admin.dto.AdminGroupResponse;
import ma.tawfir.api.admin.dto.PlatformMetricsResponse;
import ma.tawfir.api.dispute.DisputeRepository;
import ma.tawfir.api.dispute.DisputeService;
import ma.tawfir.api.dispute.dto.DisputeResponse;
import ma.tawfir.api.dispute.entity.DisputeStatus;
import ma.tawfir.api.group.ContributionScheduleRepository;
import ma.tawfir.api.group.GroupMembershipRepository;
import ma.tawfir.api.group.GroupRepository;
import ma.tawfir.api.group.entity.ContributionSchedule;
import ma.tawfir.api.group.entity.ContributionStatus;
import ma.tawfir.api.group.entity.Frequency;
import ma.tawfir.api.group.entity.Group;
import ma.tawfir.api.group.entity.GroupMembership;
import ma.tawfir.api.group.entity.GroupStatus;
import ma.tawfir.api.group.entity.MembershipRole;
import ma.tawfir.api.group.entity.PayoutOrderMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

	@Mock
	private GroupRepository groupRepository;
	@Mock
	private GroupMembershipRepository membershipRepository;
	@Mock
	private ContributionScheduleRepository contributionScheduleRepository;
	@Mock
	private DisputeRepository disputeRepository;
	@Mock
	private DisputeService disputeService;

	private AdminService adminService;

	@BeforeEach
	void setUp() {
		adminService = new AdminService(
			groupRepository, membershipRepository, contributionScheduleRepository, disputeRepository, disputeService);
	}

	private Group group(UUID id, String name, GroupStatus status, short totalCycles) {
		Group group = new Group(name, UUID.randomUUID(), BigDecimal.TEN, Frequency.MONTHLY, totalCycles, PayoutOrderMode.MANUAL);
		ReflectionTestUtils.setField(group, "id", id);
		ReflectionTestUtils.setField(group, "status", status);
		return group;
	}

	private ContributionSchedule schedule(UUID groupId, short cycle, LocalDate dueDate, ContributionStatus status) {
		ContributionSchedule schedule = new ContributionSchedule(groupId, cycle, UUID.randomUUID(), dueDate);
		ReflectionTestUtils.setField(schedule, "status", status);
		return schedule;
	}

	@Test
	void listGroups_computesMemberCountAndCyclesCompletedPerGroup() {
		UUID groupId = UUID.randomUUID();
		Group group = group(groupId, "Tontine A", GroupStatus.ACTIVE, (short) 2);
		when(groupRepository.findAll()).thenReturn(List.of(group));
		when(membershipRepository.findByGroupId(groupId)).thenReturn(List.of(
			new GroupMembership(groupId, UUID.randomUUID(), MembershipRole.ORGANIZER, (short) 1),
			new GroupMembership(groupId, UUID.randomUUID(), MembershipRole.MEMBER, (short) 2)));
		// Cycle 1: both CONFIRMED (completed). Cycle 2: one still PENDING (not completed).
		when(contributionScheduleRepository.findByGroupIdOrderByCycleNumberAscUserIdAsc(groupId)).thenReturn(List.of(
			schedule(groupId, (short) 1, LocalDate.now().minusDays(30), ContributionStatus.CONFIRMED),
			schedule(groupId, (short) 1, LocalDate.now().minusDays(30), ContributionStatus.CONFIRMED),
			schedule(groupId, (short) 2, LocalDate.now(), ContributionStatus.CONFIRMED),
			schedule(groupId, (short) 2, LocalDate.now(), ContributionStatus.PENDING)));

		List<AdminGroupResponse> result = adminService.listGroups();

		assertThat(result).hasSize(1);
		AdminGroupResponse response = result.get(0);
		assertThat(response.id()).isEqualTo(groupId);
		assertThat(response.name()).isEqualTo("Tontine A");
		assertThat(response.status()).isEqualTo(GroupStatus.ACTIVE);
		assertThat(response.memberCount()).isEqualTo(2);
		assertThat(response.totalCycles()).isEqualTo((short) 2);
		assertThat(response.cyclesCompleted()).isEqualTo(1);
	}

	@Test
	void getMetrics_computesDefaultRateAndDeduplicatesAtRiskGroups() {
		UUID lateGroupId = UUID.randomUUID();
		UUID disputedGroupId = UUID.randomUUID();
		when(groupRepository.countByStatus(GroupStatus.ACTIVE)).thenReturn(5L);
		when(contributionScheduleRepository.countByDueDateBeforeAndStatusIn(any(LocalDate.class), anyList())).thenReturn(20L);
		when(contributionScheduleRepository.countByDueDateBeforeAndStatus(any(LocalDate.class), org.mockito.ArgumentMatchers.eq(ContributionStatus.LATE)))
			.thenReturn(5L);
		when(disputeRepository.countByStatus(DisputeStatus.OPEN)).thenReturn(3L);
		when(disputeRepository.findDistinctGroupIdsByStatus(DisputeStatus.OPEN)).thenReturn(List.of(disputedGroupId, lateGroupId));
		when(contributionScheduleRepository.findDistinctGroupIdsByStatus(ContributionStatus.LATE)).thenReturn(List.of(lateGroupId));

		PlatformMetricsResponse metrics = adminService.getMetrics();

		assertThat(metrics.activeGroups()).isEqualTo(5);
		assertThat(metrics.defaultRatePercent()).isEqualTo(25.0);
		assertThat(metrics.openDisputes()).isEqualTo(3);
		// lateGroupId appears in both sources but must only count once.
		assertThat(metrics.atRiskGroups()).isEqualTo(2);
	}

	@Test
	void getMetrics_noPastDueSchedules_defaultRateIsZeroNotDivideByZero() {
		when(groupRepository.countByStatus(GroupStatus.ACTIVE)).thenReturn(0L);
		when(contributionScheduleRepository.countByDueDateBeforeAndStatusIn(any(LocalDate.class), anyList())).thenReturn(0L);
		when(disputeRepository.countByStatus(DisputeStatus.OPEN)).thenReturn(0L);
		when(disputeRepository.findDistinctGroupIdsByStatus(DisputeStatus.OPEN)).thenReturn(List.of());
		when(contributionScheduleRepository.findDistinctGroupIdsByStatus(ContributionStatus.LATE)).thenReturn(List.of());

		PlatformMetricsResponse metrics = adminService.getMetrics();

		assertThat(metrics.defaultRatePercent()).isZero();
	}

	@Test
	void listDisputes_delegatesToDisputeServiceListAll() {
		DisputeResponse response = new DisputeResponse(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
			UUID.randomUUID(), "reason", null, DisputeStatus.OPEN, null, null, null, null);
		when(disputeService.listAll()).thenReturn(List.of(response));

		List<DisputeResponse> result = adminService.listDisputes();

		assertThat(result).containsExactly(response);
	}

}
