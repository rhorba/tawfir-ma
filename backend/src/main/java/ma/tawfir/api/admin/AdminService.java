package ma.tawfir.api.admin;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
import ma.tawfir.api.group.entity.Group;
import ma.tawfir.api.group.entity.GroupStatus;
import org.springframework.stereotype.Service;

/**
 * Platform-wide, unscoped reads for the admin dashboard (story 6.1). Every
 * method here assumes the caller has already been through
 * AdminController#requireAdmin — there is no per-group membership check,
 * unlike the member-facing services (GroupService, DisputeService et al.).
 */
@Service
public class AdminService {

	private static final List<ContributionStatus> ALL_CONTRIBUTION_STATUSES = List.of(ContributionStatus.values());

	private final GroupRepository groupRepository;
	private final GroupMembershipRepository membershipRepository;
	private final ContributionScheduleRepository contributionScheduleRepository;
	private final DisputeRepository disputeRepository;
	private final DisputeService disputeService;

	public AdminService(GroupRepository groupRepository, GroupMembershipRepository membershipRepository,
			ContributionScheduleRepository contributionScheduleRepository, DisputeRepository disputeRepository,
			DisputeService disputeService) {
		this.groupRepository = groupRepository;
		this.membershipRepository = membershipRepository;
		this.contributionScheduleRepository = contributionScheduleRepository;
		this.disputeRepository = disputeRepository;
		this.disputeService = disputeService;
	}

	public List<AdminGroupResponse> listGroups() {
		return groupRepository.findAll().stream()
			.map(this::toAdminGroupResponse)
			.toList();
	}

	/**
	 * defaultRatePercent = share of past-due contribution schedules that ended
	 * up LATE (still unpaid after their due date), out of all past-due
	 * schedules regardless of final status (decisions.md 2026-07-31).
	 * atRiskGroups = groups with at least one OPEN dispute or one LATE
	 * contribution schedule (union, de-duplicated by group id).
	 */
	public PlatformMetricsResponse getMetrics() {
		LocalDate today = LocalDate.now();

		long activeGroups = groupRepository.countByStatus(GroupStatus.ACTIVE);

		long pastDueTotal = contributionScheduleRepository.countByDueDateBeforeAndStatusIn(today, ALL_CONTRIBUTION_STATUSES);
		long pastDueLate = contributionScheduleRepository.countByDueDateBeforeAndStatus(today, ContributionStatus.LATE);
		double defaultRatePercent = pastDueTotal == 0 ? 0.0 : roundToTwoDecimals(pastDueLate * 100.0 / pastDueTotal);

		long openDisputes = disputeRepository.countByStatus(DisputeStatus.OPEN);

		Set<UUID> atRiskGroupIds = new HashSet<>(disputeRepository.findDistinctGroupIdsByStatus(DisputeStatus.OPEN));
		atRiskGroupIds.addAll(contributionScheduleRepository.findDistinctGroupIdsByStatus(ContributionStatus.LATE));

		return new PlatformMetricsResponse(
			(int) activeGroups, defaultRatePercent, (int) openDisputes, atRiskGroupIds.size());
	}

	public List<DisputeResponse> listDisputes() {
		return disputeService.listAll();
	}

	private AdminGroupResponse toAdminGroupResponse(Group group) {
		int memberCount = membershipRepository.findByGroupId(group.getId()).size();
		int cyclesCompleted = countCompletedCycles(group.getId());
		return new AdminGroupResponse(
			group.getId(), group.getName(), group.getStatus(), memberCount, group.getTotalCycles(), cyclesCompleted);
	}

	/** A cycle counts as completed once every member's contribution for it is CONFIRMED. */
	private int countCompletedCycles(UUID groupId) {
		Map<Short, List<ContributionSchedule>> byCycle = contributionScheduleRepository
			.findByGroupIdOrderByCycleNumberAscUserIdAsc(groupId).stream()
			.collect(Collectors.groupingBy(ContributionSchedule::getCycleNumber));
		return (int) byCycle.values().stream()
			.filter(schedules -> schedules.stream().allMatch(s -> s.getStatus() == ContributionStatus.CONFIRMED))
			.count();
	}

	private static double roundToTwoDecimals(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

}
