package ma.tawfir.api.group;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import ma.tawfir.api.common.ForbiddenException;
import ma.tawfir.api.common.NotFoundException;
import ma.tawfir.api.common.ValidationException;
import ma.tawfir.api.group.dto.ContributionResponse;
import ma.tawfir.api.group.entity.ContributionSchedule;
import ma.tawfir.api.group.entity.ContributionStatus;
import ma.tawfir.api.group.entity.Group;
import ma.tawfir.api.group.entity.GroupMembership;
import ma.tawfir.api.group.entity.MembershipRole;
import ma.tawfir.api.ledger.LedgerEntryRepository;
import ma.tawfir.api.ledger.entity.LedgerEntry;
import ma.tawfir.api.ledger.entity.LedgerSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mark-paid/confirm both use an atomic conditional UPDATE (see
 * ContributionScheduleRepository#compareAndSetStatus) rather than read-then-
 * write, so two concurrent requests for the same contribution can't both
 * succeed and double-append a ledger entry (test-strategy-tawfir.md §4).
 */
@Service
public class ContributionService {

	private static final Set<ContributionStatus> PAYABLE_STATUSES =
		Set.of(ContributionStatus.PENDING, ContributionStatus.LATE);

	private final ContributionScheduleRepository contributionScheduleRepository;
	private final GroupMembershipRepository membershipRepository;
	private final GroupRepository groupRepository;
	private final LedgerEntryRepository ledgerEntryRepository;

	public ContributionService(ContributionScheduleRepository contributionScheduleRepository,
			GroupMembershipRepository membershipRepository, GroupRepository groupRepository,
			LedgerEntryRepository ledgerEntryRepository) {
		this.contributionScheduleRepository = contributionScheduleRepository;
		this.membershipRepository = membershipRepository;
		this.groupRepository = groupRepository;
		this.ledgerEntryRepository = ledgerEntryRepository;
	}

	@Transactional
	public ContributionResponse markPaid(UUID actingUserId, UUID groupId, UUID scheduleId) {
		ContributionSchedule schedule = requireSchedule(groupId, scheduleId);
		if (!schedule.getUserId().equals(actingUserId)) {
			throw new ForbiddenException("You can only mark your own contribution as paid");
		}

		int updated = contributionScheduleRepository.compareAndSetStatus(
			scheduleId, PAYABLE_STATUSES, ContributionStatus.MARKED_PAID);
		if (updated == 0) {
			throw new ValidationException("Contribution is not in a payable state");
		}

		return toResponse(requireSchedule(groupId, scheduleId));
	}

	@Transactional
	public ContributionResponse confirm(UUID actingUserId, UUID groupId, UUID scheduleId) {
		requireOrganizer(groupId, actingUserId);
		requireSchedule(groupId, scheduleId);

		int updated = contributionScheduleRepository.compareAndSetStatus(
			scheduleId, Set.of(ContributionStatus.MARKED_PAID), ContributionStatus.CONFIRMED);
		if (updated == 0) {
			throw new ValidationException("Contribution must be marked paid before it can be confirmed");
		}

		Group group = groupRepository.findById(groupId)
			.orElseThrow(() -> new NotFoundException("Group not found: " + groupId));
		ledgerEntryRepository.save(LedgerEntry.contribution(
			groupId, scheduleId, actingUserId, group.getContributionAmount(), LedgerSource.ORGANIZER_CONFIRMED));

		return toResponse(requireSchedule(groupId, scheduleId));
	}

	public List<ContributionResponse> listForGroup(UUID actingUserId, UUID groupId, boolean isAdmin) {
		if (!isAdmin && !membershipRepository.existsByGroupIdAndUserId(groupId, actingUserId)) {
			throw new ForbiddenException("You are not a member of this group");
		}
		return contributionScheduleRepository.findByGroupIdOrderByCycleNumberAscUserIdAsc(groupId).stream()
			.map(this::toResponse)
			.toList();
	}

	private void requireOrganizer(UUID groupId, UUID userId) {
		GroupMembership membership = membershipRepository.findByGroupIdAndUserId(groupId, userId)
			.orElseThrow(() -> new ForbiddenException("You are not a member of this group"));
		if (membership.getRoleInGroup() != MembershipRole.ORGANIZER) {
			throw new ForbiddenException("Only the group organizer can confirm a contribution");
		}
	}

	private ContributionSchedule requireSchedule(UUID groupId, UUID scheduleId) {
		ContributionSchedule schedule = contributionScheduleRepository.findById(scheduleId)
			.orElseThrow(() -> new NotFoundException("Contribution not found: " + scheduleId));
		if (!schedule.getGroupId().equals(groupId)) {
			throw new NotFoundException("Contribution not found: " + scheduleId);
		}
		return schedule;
	}

	private ContributionResponse toResponse(ContributionSchedule schedule) {
		return new ContributionResponse(schedule.getId(), schedule.getGroupId(), schedule.getCycleNumber(),
			schedule.getUserId(), schedule.getDueDate(), schedule.getStatus());
	}

}
