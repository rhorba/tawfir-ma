package ma.tawfir.api.group;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import ma.tawfir.api.common.ForbiddenException;
import ma.tawfir.api.common.ValidationException;
import ma.tawfir.api.group.dto.CreateGroupRequest;
import ma.tawfir.api.group.dto.GroupDetailResponse;
import ma.tawfir.api.group.dto.GroupSummaryResponse;
import ma.tawfir.api.group.dto.MemberResponse;
import ma.tawfir.api.group.entity.ContributionSchedule;
import ma.tawfir.api.group.entity.Group;
import ma.tawfir.api.group.entity.GroupMembership;
import ma.tawfir.api.group.entity.MembershipRole;
import ma.tawfir.api.group.entity.PayoutOrderMode;
import ma.tawfir.api.group.entity.PayoutSchedule;
import ma.tawfir.api.user.UserRepository;
import ma.tawfir.api.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Group-scoped endpoints need a relationship check ("is this user actually a
 * member of THIS group"), not just a role check — RBAC alone is insufficient
 * since ORGANIZER is a per-group attribute, not a global role
 * (security-tawfir.md §4). Implemented here as explicit service-layer checks
 * rather than full ReBAC infrastructure, per that doc's YAGNI note.
 */
@Service
public class GroupService {

	private final GroupRepository groupRepository;
	private final GroupMembershipRepository membershipRepository;
	private final ContributionScheduleRepository contributionScheduleRepository;
	private final PayoutScheduleRepository payoutScheduleRepository;
	private final UserRepository userRepository;

	public GroupService(GroupRepository groupRepository, GroupMembershipRepository membershipRepository,
			ContributionScheduleRepository contributionScheduleRepository,
			PayoutScheduleRepository payoutScheduleRepository, UserRepository userRepository) {
		this.groupRepository = groupRepository;
		this.membershipRepository = membershipRepository;
		this.contributionScheduleRepository = contributionScheduleRepository;
		this.payoutScheduleRepository = payoutScheduleRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public Group createGroup(UUID organizerId, CreateGroupRequest request) {
		Set<String> uniquePhones = new LinkedHashSet<>(request.members());
		if (uniquePhones.size() != request.members().size()) {
			throw new ValidationException("members list contains a duplicate phone number");
		}

		User organizer = userRepository.findById(organizerId)
			.orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + organizerId));

		List<String> orderedPhones = new ArrayList<>(uniquePhones);
		if (!orderedPhones.contains(organizer.getPhoneNumber())) {
			orderedPhones.add(organizer.getPhoneNumber());
		}

		Group group = new Group(request.name(), organizerId, request.contributionAmount(),
			request.frequency(), request.totalCycles(), request.payoutOrderMode());
		group = groupRepository.save(group);

		boolean manual = request.payoutOrderMode() == PayoutOrderMode.MANUAL;
		for (int i = 0; i < orderedPhones.size(); i++) {
			String phoneNumber = orderedPhones.get(i);
			User member = userRepository.findByPhoneNumber(phoneNumber)
				.orElseGet(() -> userRepository.save(new User(phoneNumber)));
			MembershipRole role = phoneNumber.equals(organizer.getPhoneNumber())
				? MembershipRole.ORGANIZER
				: MembershipRole.MEMBER;
			Short payoutPosition = manual ? (short) (i + 1) : null;
			membershipRepository.save(new GroupMembership(group.getId(), member.getId(), role, payoutPosition));
		}

		return group;
	}

	@Transactional
	public Group finalizeGroup(UUID actingUserId, UUID groupId) {
		Group group = requireGroup(groupId);
		GroupMembership actingMembership = membershipRepository.findByGroupIdAndUserId(groupId, actingUserId)
			.orElseThrow(() -> new ForbiddenException("You are not a member of this group"));
		if (actingMembership.getRoleInGroup() != MembershipRole.ORGANIZER) {
			throw new ForbiddenException("Only the group organizer can finalize this group");
		}
		if (!group.isDraft()) {
			throw new ValidationException("Group is not in DRAFT status");
		}

		List<GroupMembership> memberships = membershipRepository.findByGroupId(groupId);
		if (memberships.size() != group.getTotalCycles()) {
			throw new ValidationException(
				"Member count (%d) must equal total_cycles (%d) to finalize"
					.formatted(memberships.size(), group.getTotalCycles()));
		}

		assignPayoutPositions(group, memberships);
		generateSchedules(group, memberships);

		group.activate();
		return groupRepository.save(group);
	}

	public List<GroupSummaryResponse> listGroupsForUser(UUID userId) {
		return membershipRepository.findByUserId(userId).stream()
			.map(membership -> groupRepository.findById(membership.getGroupId()).orElseThrow())
			.map(group -> new GroupSummaryResponse(
				group.getId(), group.getName(), group.getContributionAmount(), group.getCurrency(),
				group.getFrequency(), group.getTotalCycles(), group.getStatus(),
				membershipRepository.findByGroupId(group.getId()).size()))
			.toList();
	}

	public GroupDetailResponse getGroupDetail(UUID userId, UUID groupId, boolean isAdmin) {
		Group group = requireGroup(groupId);
		if (!isAdmin && !membershipRepository.existsByGroupIdAndUserId(groupId, userId)) {
			throw new ForbiddenException("You are not a member of this group");
		}

		List<GroupMembership> memberships = membershipRepository.findByGroupId(groupId);
		Map<UUID, User> usersById = userRepository.findAllById(memberships.stream().map(GroupMembership::getUserId).toList())
			.stream()
			.collect(Collectors.toMap(User::getId, Function.identity()));

		List<MemberResponse> members = memberships.stream()
			.map(membership -> new MemberResponse(
				membership.getUserId(),
				usersById.get(membership.getUserId()).getPhoneNumber(),
				membership.getRoleInGroup(),
				membership.getPayoutPosition()))
			.toList();

		return new GroupDetailResponse(group.getId(), group.getName(), group.getOrganizerId(),
			group.getContributionAmount(), group.getCurrency(), group.getFrequency(), group.getTotalCycles(),
			group.getPayoutOrderMode(), group.getStatus(), members);
	}

	private Group requireGroup(UUID groupId) {
		return groupRepository.findById(groupId)
			.orElseThrow(() -> new GroupNotFoundException("Group not found: " + groupId));
	}

	private void assignPayoutPositions(Group group, List<GroupMembership> memberships) {
		if (group.getPayoutOrderMode() == PayoutOrderMode.RANDOMIZED) {
			List<GroupMembership> shuffled = new ArrayList<>(memberships);
			Collections.shuffle(shuffled);
			for (int i = 0; i < shuffled.size(); i++) {
				shuffled.get(i).assignPayoutPosition((short) (i + 1));
				membershipRepository.save(shuffled.get(i));
			}
		} else if (memberships.stream().anyMatch(m -> m.getPayoutPosition() == null)) {
			throw new ValidationException("Every member must have a payout position assigned for a MANUAL group");
		}
	}

	private void generateSchedules(Group group, List<GroupMembership> memberships) {
		LocalDate baseDate = LocalDate.now();
		BigDecimal payoutAmount = group.getContributionAmount().multiply(BigDecimal.valueOf(memberships.size()));
		Map<Short, UUID> recipientByCycle = memberships.stream()
			.collect(Collectors.toMap(GroupMembership::getPayoutPosition, GroupMembership::getUserId));

		for (short cycle = 1; cycle <= group.getTotalCycles(); cycle++) {
			LocalDate dueDate = group.getFrequency().advance(baseDate, cycle);
			for (GroupMembership membership : memberships) {
				contributionScheduleRepository.save(
					new ContributionSchedule(group.getId(), cycle, membership.getUserId(), dueDate));
			}
			payoutScheduleRepository.save(new PayoutSchedule(
				group.getId(), cycle, recipientByCycle.get(cycle), dueDate, payoutAmount));
		}
	}

}
