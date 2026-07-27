package ma.tawfir.api.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import ma.tawfir.api.common.ForbiddenException;
import ma.tawfir.api.common.ValidationException;
import ma.tawfir.api.group.dto.CreateGroupRequest;
import ma.tawfir.api.group.dto.GroupDetailResponse;
import ma.tawfir.api.group.dto.GroupSummaryResponse;
import ma.tawfir.api.group.entity.ContributionSchedule;
import ma.tawfir.api.group.entity.Frequency;
import ma.tawfir.api.group.entity.Group;
import ma.tawfir.api.group.entity.GroupMembership;
import ma.tawfir.api.group.entity.GroupStatus;
import ma.tawfir.api.group.entity.MembershipRole;
import ma.tawfir.api.group.entity.PayoutOrderMode;
import ma.tawfir.api.group.entity.PayoutSchedule;
import ma.tawfir.api.user.UserRepository;
import ma.tawfir.api.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

	@Mock
	private GroupRepository groupRepository;
	@Mock
	private GroupMembershipRepository membershipRepository;
	@Mock
	private ContributionScheduleRepository contributionScheduleRepository;
	@Mock
	private PayoutScheduleRepository payoutScheduleRepository;
	@Mock
	private UserRepository userRepository;

	private GroupService groupService;

	@BeforeEach
	void setUp() {
		groupService = new GroupService(groupRepository, membershipRepository, contributionScheduleRepository,
			payoutScheduleRepository, userRepository);
		lenient().when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> {
			Group group = invocation.getArgument(0);
			if (group.getId() == null) {
				ReflectionTestUtils.setField(group, "id", UUID.randomUUID());
			}
			return group;
		});
		lenient().when(membershipRepository.save(any(GroupMembership.class))).thenAnswer(invocation -> {
			GroupMembership membership = invocation.getArgument(0);
			if (membership.getId() == null) {
				ReflectionTestUtils.setField(membership, "id", UUID.randomUUID());
			}
			return membership;
		});
	}

	private User userWithId(String phoneNumber) {
		User user = new User(phoneNumber);
		ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
		return user;
	}

	@Test
	void createGroup_manualOrder_assignsPayoutPositionByArrayOrder() {
		User organizer = userWithId("+212600000001");
		when(userRepository.findById(organizer.getId())).thenReturn(Optional.of(organizer));
		when(userRepository.findByPhoneNumber(organizer.getPhoneNumber())).thenReturn(Optional.of(organizer));
		User memberA = userWithId("+212600000002");
		when(userRepository.findByPhoneNumber(memberA.getPhoneNumber())).thenReturn(Optional.of(memberA));

		CreateGroupRequest request = new CreateGroupRequest("Daret", BigDecimal.valueOf(500), Frequency.MONTHLY,
			(short) 2, PayoutOrderMode.MANUAL, List.of(organizer.getPhoneNumber(), memberA.getPhoneNumber()));

		groupService.createGroup(organizer.getId(), request);

		ArgumentCaptor<GroupMembership> captor = ArgumentCaptor.forClass(GroupMembership.class);
		verify(membershipRepository, org.mockito.Mockito.times(2)).save(captor.capture());
		List<GroupMembership> saved = captor.getAllValues();
		assertThat(saved.get(0).getPayoutPosition()).isEqualTo((short) 1);
		assertThat(saved.get(0).getRoleInGroup()).isEqualTo(MembershipRole.ORGANIZER);
		assertThat(saved.get(1).getPayoutPosition()).isEqualTo((short) 2);
		assertThat(saved.get(1).getRoleInGroup()).isEqualTo(MembershipRole.MEMBER);
	}

	@Test
	void createGroup_organizerOmittedFromMembers_isAutoAppended() {
		User organizer = userWithId("+212600000001");
		when(userRepository.findById(organizer.getId())).thenReturn(Optional.of(organizer));
		User memberA = userWithId("+212600000002");
		when(userRepository.findByPhoneNumber(memberA.getPhoneNumber())).thenReturn(Optional.of(memberA));
		when(userRepository.findByPhoneNumber(organizer.getPhoneNumber())).thenReturn(Optional.of(organizer));

		CreateGroupRequest request = new CreateGroupRequest("Daret", BigDecimal.valueOf(500), Frequency.MONTHLY,
			(short) 2, PayoutOrderMode.RANDOMIZED, List.of(memberA.getPhoneNumber()));

		groupService.createGroup(organizer.getId(), request);

		ArgumentCaptor<GroupMembership> captor = ArgumentCaptor.forClass(GroupMembership.class);
		verify(membershipRepository, org.mockito.Mockito.times(2)).save(captor.capture());
		assertThat(captor.getAllValues())
			.anyMatch(m -> m.getUserId().equals(organizer.getId()) && m.getRoleInGroup() == MembershipRole.ORGANIZER);
		assertThat(captor.getAllValues()).allMatch(m -> m.getPayoutPosition() == null);
	}

	@Test
	void createGroup_duplicatePhoneNumber_throwsValidation() {
		UUID organizerId = UUID.randomUUID();
		CreateGroupRequest request = new CreateGroupRequest("Daret", BigDecimal.valueOf(500), Frequency.MONTHLY,
			(short) 1, PayoutOrderMode.MANUAL, List.of("+212600000002", "+212600000002"));

		assertThatThrownBy(() -> groupService.createGroup(organizerId, request))
			.isInstanceOf(ValidationException.class);
		verify(userRepository, never()).findById(any());
	}

	@Test
	void createGroup_unregisteredPhone_autoCreatesUser() {
		User organizer = userWithId("+212600000001");
		when(userRepository.findById(organizer.getId())).thenReturn(Optional.of(organizer));
		when(userRepository.findByPhoneNumber(organizer.getPhoneNumber())).thenReturn(Optional.of(organizer));
		when(userRepository.findByPhoneNumber("+212600000099")).thenReturn(Optional.empty());
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
			User u = invocation.getArgument(0);
			ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
			return u;
		});

		CreateGroupRequest request = new CreateGroupRequest("Daret", BigDecimal.valueOf(500), Frequency.MONTHLY,
			(short) 2, PayoutOrderMode.MANUAL, List.of(organizer.getPhoneNumber(), "+212600000099"));

		groupService.createGroup(organizer.getId(), request);

		verify(userRepository).save(any(User.class));
	}

	@Test
	void finalizeGroup_nonMember_throwsForbidden() {
		UUID groupId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		when(groupRepository.findById(groupId)).thenReturn(Optional.of(draftGroup(groupId, (short) 2)));
		when(membershipRepository.findByGroupIdAndUserId(groupId, userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> groupService.finalizeGroup(userId, groupId))
			.isInstanceOf(ForbiddenException.class);
	}

	@Test
	void finalizeGroup_memberNotOrganizer_throwsForbidden() {
		UUID groupId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		when(groupRepository.findById(groupId)).thenReturn(Optional.of(draftGroup(groupId, (short) 2)));
		when(membershipRepository.findByGroupIdAndUserId(groupId, userId))
			.thenReturn(Optional.of(new GroupMembership(groupId, userId, MembershipRole.MEMBER, (short) 1)));

		assertThatThrownBy(() -> groupService.finalizeGroup(userId, groupId))
			.isInstanceOf(ForbiddenException.class);
	}

	@Test
	void finalizeGroup_notDraft_throwsValidation() {
		UUID groupId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		Group group = draftGroup(groupId, (short) 1);
		group.activate();
		when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
		when(membershipRepository.findByGroupIdAndUserId(groupId, userId))
			.thenReturn(Optional.of(new GroupMembership(groupId, userId, MembershipRole.ORGANIZER, (short) 1)));

		assertThatThrownBy(() -> groupService.finalizeGroup(userId, groupId))
			.isInstanceOf(ValidationException.class);
	}

	@Test
	void finalizeGroup_memberCountMismatchesTotalCycles_throwsValidation() {
		UUID groupId = UUID.randomUUID();
		UUID organizerId = UUID.randomUUID();
		when(groupRepository.findById(groupId)).thenReturn(Optional.of(draftGroup(groupId, (short) 3)));
		when(membershipRepository.findByGroupIdAndUserId(groupId, organizerId))
			.thenReturn(Optional.of(new GroupMembership(groupId, organizerId, MembershipRole.ORGANIZER, (short) 1)));
		when(membershipRepository.findByGroupId(groupId)).thenReturn(List.of(
			new GroupMembership(groupId, organizerId, MembershipRole.ORGANIZER, (short) 1)));

		assertThatThrownBy(() -> groupService.finalizeGroup(organizerId, groupId))
			.isInstanceOf(ValidationException.class);
		verify(contributionScheduleRepository, never()).save(any());
	}

	@Test
	void finalizeGroup_randomized_assignsAllPositionsAndGeneratesSchedules() {
		UUID groupId = UUID.randomUUID();
		UUID organizerId = UUID.randomUUID();
		UUID memberId = UUID.randomUUID();
		Group group = draftGroup(groupId, (short) 2);
		group = spySave(group);
		when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
		GroupMembership organizerMembership = new GroupMembership(groupId, organizerId, MembershipRole.ORGANIZER, null);
		GroupMembership memberMembership = new GroupMembership(groupId, memberId, MembershipRole.MEMBER, null);
		when(membershipRepository.findByGroupIdAndUserId(groupId, organizerId)).thenReturn(Optional.of(organizerMembership));
		when(membershipRepository.findByGroupId(groupId)).thenReturn(List.of(organizerMembership, memberMembership));

		groupService.finalizeGroup(organizerId, groupId);

		assertThat(organizerMembership.getPayoutPosition()).isNotNull();
		assertThat(memberMembership.getPayoutPosition()).isNotNull();
		assertThat(Set.of(organizerMembership.getPayoutPosition(), memberMembership.getPayoutPosition()))
			.containsExactlyInAnyOrder((short) 1, (short) 2);
		verify(contributionScheduleRepository, org.mockito.Mockito.times(4)).save(any(ContributionSchedule.class));
		verify(payoutScheduleRepository, org.mockito.Mockito.times(2)).save(any(PayoutSchedule.class));
		assertThat(group.getStatus()).isEqualTo(GroupStatus.ACTIVE);
	}

	@Test
	void finalizeGroup_manualWithMissingPosition_throwsValidation() {
		UUID groupId = UUID.randomUUID();
		UUID organizerId = UUID.randomUUID();
		Group group = draftGroup(groupId, (short) 1, PayoutOrderMode.MANUAL);
		when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
		GroupMembership organizerMembership = new GroupMembership(groupId, organizerId, MembershipRole.ORGANIZER, null);
		when(membershipRepository.findByGroupIdAndUserId(groupId, organizerId)).thenReturn(Optional.of(organizerMembership));
		when(membershipRepository.findByGroupId(groupId)).thenReturn(List.of(organizerMembership));

		assertThatThrownBy(() -> groupService.finalizeGroup(organizerId, groupId))
			.isInstanceOf(ValidationException.class);
	}

	@Test
	void getGroupDetail_nonMember_throwsForbidden() {
		UUID groupId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		when(groupRepository.findById(groupId)).thenReturn(Optional.of(draftGroup(groupId, (short) 1)));
		when(membershipRepository.existsByGroupIdAndUserId(groupId, userId)).thenReturn(false);

		assertThatThrownBy(() -> groupService.getGroupDetail(userId, groupId, false))
			.isInstanceOf(ForbiddenException.class);
	}

	@Test
	void getGroupDetail_admin_bypassesMembershipCheck() {
		UUID groupId = UUID.randomUUID();
		UUID adminId = UUID.randomUUID();
		when(groupRepository.findById(groupId)).thenReturn(Optional.of(draftGroup(groupId, (short) 1)));
		when(membershipRepository.findByGroupId(groupId)).thenReturn(List.of());
		when(userRepository.findAllById(any())).thenReturn(List.of());

		GroupDetailResponse response = groupService.getGroupDetail(adminId, groupId, true);

		assertThat(response.id()).isEqualTo(groupId);
		verify(membershipRepository, never()).existsByGroupIdAndUserId(any(), any());
	}

	@Test
	void getGroupDetail_notFound_throwsGroupNotFound() {
		UUID groupId = UUID.randomUUID();
		when(groupRepository.findById(groupId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> groupService.getGroupDetail(UUID.randomUUID(), groupId, false))
			.isInstanceOf(GroupNotFoundException.class);
	}

	@Test
	void listGroupsForUser_mapsMembershipsToSummaries() {
		UUID userId = UUID.randomUUID();
		UUID groupId = UUID.randomUUID();
		Group group = draftGroup(groupId, (short) 2);
		when(membershipRepository.findByUserId(userId)).thenReturn(
			List.of(new GroupMembership(groupId, userId, MembershipRole.ORGANIZER, null)));
		when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
		when(membershipRepository.findByGroupId(groupId)).thenReturn(List.of(
			new GroupMembership(groupId, userId, MembershipRole.ORGANIZER, null)));

		List<GroupSummaryResponse> summaries = groupService.listGroupsForUser(userId);

		assertThat(summaries).hasSize(1);
		assertThat(summaries.get(0).id()).isEqualTo(groupId);
		assertThat(summaries.get(0).memberCount()).isEqualTo(1);
	}

	private Group draftGroup(UUID id, short totalCycles) {
		return draftGroup(id, totalCycles, PayoutOrderMode.RANDOMIZED);
	}

	private Group draftGroup(UUID id, short totalCycles, PayoutOrderMode mode) {
		Group group = new Group("Daret", UUID.randomUUID(), BigDecimal.valueOf(500), Frequency.MONTHLY,
			totalCycles, mode);
		ReflectionTestUtils.setField(group, "id", id);
		return group;
	}

	private Group spySave(Group group) {
		when(groupRepository.save(group)).thenReturn(group);
		return group;
	}

}
