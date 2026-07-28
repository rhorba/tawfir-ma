package ma.tawfir.api.dispute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import ma.tawfir.api.common.ForbiddenException;
import ma.tawfir.api.common.NotFoundException;
import ma.tawfir.api.common.ValidationException;
import ma.tawfir.api.dispute.dto.DisputeResponse;
import ma.tawfir.api.dispute.dto.OpenDisputeRequest;
import ma.tawfir.api.dispute.dto.ResolveDisputeRequest;
import ma.tawfir.api.dispute.entity.Dispute;
import ma.tawfir.api.dispute.entity.DisputeStatus;
import ma.tawfir.api.group.ContributionScheduleRepository;
import ma.tawfir.api.group.GroupMembershipRepository;
import ma.tawfir.api.group.entity.ContributionStatus;
import ma.tawfir.api.group.entity.GroupMembership;
import ma.tawfir.api.group.entity.MembershipRole;
import ma.tawfir.api.ledger.LedgerEntryRepository;
import ma.tawfir.api.ledger.entity.LedgerEntry;
import ma.tawfir.api.ledger.entity.LedgerSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DisputeServiceTest {

	@Mock
	private DisputeRepository disputeRepository;
	@Mock
	private LedgerEntryRepository ledgerEntryRepository;
	@Mock
	private ContributionScheduleRepository contributionScheduleRepository;
	@Mock
	private GroupMembershipRepository membershipRepository;

	private DisputeService disputeService;

	@BeforeEach
	void setUp() {
		disputeService = new DisputeService(
			disputeRepository, ledgerEntryRepository, contributionScheduleRepository, membershipRepository);
	}

	private Dispute dispute(UUID id, UUID groupId, UUID ledgerEntryId, UUID raisedByUserId) {
		Dispute dispute = new Dispute(groupId, ledgerEntryId, raisedByUserId, "wrong amount", null);
		ReflectionTestUtils.setField(dispute, "id", id);
		return dispute;
	}

	@Test
	void openDispute_confirmedContribution_createsOpenDisputeAndFlagsScheduleDisputed() {
		UUID groupId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID ledgerEntryId = UUID.randomUUID();
		UUID scheduleId = UUID.randomUUID();
		when(membershipRepository.existsByGroupIdAndUserId(groupId, userId)).thenReturn(true);
		LedgerEntry ledgerEntry = LedgerEntry.contribution(
			groupId, scheduleId, UUID.randomUUID(), java.math.BigDecimal.TEN, LedgerSource.ORGANIZER_CONFIRMED);
		when(ledgerEntryRepository.findById(ledgerEntryId)).thenReturn(Optional.of(ledgerEntry));
		when(contributionScheduleRepository.compareAndSetStatus(eq(scheduleId), anySet(), eq(ContributionStatus.DISPUTED)))
			.thenReturn(1);
		when(disputeRepository.save(any(Dispute.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));

		DisputeResponse response = disputeService.openDispute(
			userId, groupId, new OpenDisputeRequest(ledgerEntryId, "wrong amount", null), false);

		assertThat(response.status()).isEqualTo(DisputeStatus.OPEN);
		assertThat(response.raisedByUserId()).isEqualTo(userId);
	}

	@Test
	void openDispute_nonMember_throwsForbidden() {
		UUID groupId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		when(membershipRepository.existsByGroupIdAndUserId(groupId, userId)).thenReturn(false);

		assertThatThrownBy(() -> disputeService.openDispute(
				userId, groupId, new OpenDisputeRequest(UUID.randomUUID(), "reason", null), false))
			.isInstanceOf(ForbiddenException.class);
		verify(ledgerEntryRepository, never()).findById(any());
	}

	@Test
	void openDispute_ledgerEntryFromDifferentGroup_throwsNotFound() {
		UUID groupId = UUID.randomUUID();
		UUID otherGroupId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID ledgerEntryId = UUID.randomUUID();
		when(membershipRepository.existsByGroupIdAndUserId(groupId, userId)).thenReturn(true);
		LedgerEntry ledgerEntry = LedgerEntry.contribution(
			otherGroupId, UUID.randomUUID(), UUID.randomUUID(), java.math.BigDecimal.TEN, LedgerSource.ORGANIZER_CONFIRMED);
		when(ledgerEntryRepository.findById(ledgerEntryId)).thenReturn(Optional.of(ledgerEntry));

		assertThatThrownBy(() -> disputeService.openDispute(
				userId, groupId, new OpenDisputeRequest(ledgerEntryId, "reason", null), false))
			.isInstanceOf(NotFoundException.class);
	}

	@Test
	void openDispute_scheduleNotConfirmed_throwsValidation() {
		UUID groupId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID ledgerEntryId = UUID.randomUUID();
		UUID scheduleId = UUID.randomUUID();
		when(membershipRepository.existsByGroupIdAndUserId(groupId, userId)).thenReturn(true);
		LedgerEntry ledgerEntry = LedgerEntry.contribution(
			groupId, scheduleId, UUID.randomUUID(), java.math.BigDecimal.TEN, LedgerSource.ORGANIZER_CONFIRMED);
		when(ledgerEntryRepository.findById(ledgerEntryId)).thenReturn(Optional.of(ledgerEntry));
		when(contributionScheduleRepository.compareAndSetStatus(eq(scheduleId), anySet(), eq(ContributionStatus.DISPUTED)))
			.thenReturn(0);

		assertThatThrownBy(() -> disputeService.openDispute(
				userId, groupId, new OpenDisputeRequest(ledgerEntryId, "reason", null), false))
			.isInstanceOf(ValidationException.class);
		verify(disputeRepository, never()).save(any());
	}

	@Test
	void resolveDispute_organizerAccepts_updatesDispute() {
		UUID groupId = UUID.randomUUID();
		UUID disputeId = UUID.randomUUID();
		UUID organizerId = UUID.randomUUID();
		Dispute open = dispute(disputeId, groupId, UUID.randomUUID(), UUID.randomUUID());
		Dispute resolved = dispute(disputeId, groupId, open.getLedgerEntryId(), open.getRaisedByUserId());
		resolved.resolve(DisputeStatus.ACCEPTED, organizerId, "confirmed error");
		when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(open)).thenReturn(Optional.of(resolved));
		when(membershipRepository.findByGroupIdAndUserId(groupId, organizerId))
			.thenReturn(Optional.of(new GroupMembership(groupId, organizerId, MembershipRole.ORGANIZER, (short) 1)));
		when(disputeRepository.resolveIfOpen(eq(disputeId), eq(DisputeStatus.ACCEPTED), eq(organizerId), any(), any(Instant.class)))
			.thenReturn(1);

		DisputeResponse response = disputeService.resolveDispute(
			organizerId, disputeId, new ResolveDisputeRequest(DisputeStatus.ACCEPTED, "confirmed error"), false);

		assertThat(response.status()).isEqualTo(DisputeStatus.ACCEPTED);
		assertThat(response.resolvedByUserId()).isEqualTo(organizerId);
	}

	@Test
	void resolveDispute_nonOrganizerMember_throwsForbiddenWithoutTouchingDispute() {
		UUID groupId = UUID.randomUUID();
		UUID disputeId = UUID.randomUUID();
		UUID memberId = UUID.randomUUID();
		Dispute open = dispute(disputeId, groupId, UUID.randomUUID(), UUID.randomUUID());
		when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(open));
		when(membershipRepository.findByGroupIdAndUserId(groupId, memberId))
			.thenReturn(Optional.of(new GroupMembership(groupId, memberId, MembershipRole.MEMBER, (short) 1)));

		assertThatThrownBy(() -> disputeService.resolveDispute(
				memberId, disputeId, new ResolveDisputeRequest(DisputeStatus.ACCEPTED, "reason"), false))
			.isInstanceOf(ForbiddenException.class);
		verify(disputeRepository, never()).resolveIfOpen(any(), any(), any(), any(), any());
	}

	@Test
	void resolveDispute_alreadyResolved_throwsValidation() {
		UUID groupId = UUID.randomUUID();
		UUID disputeId = UUID.randomUUID();
		UUID organizerId = UUID.randomUUID();
		Dispute open = dispute(disputeId, groupId, UUID.randomUUID(), UUID.randomUUID());
		when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(open));
		when(membershipRepository.findByGroupIdAndUserId(groupId, organizerId))
			.thenReturn(Optional.of(new GroupMembership(groupId, organizerId, MembershipRole.ORGANIZER, (short) 1)));
		when(disputeRepository.resolveIfOpen(eq(disputeId), eq(DisputeStatus.REJECTED), eq(organizerId), any(), any(Instant.class)))
			.thenReturn(0);

		assertThatThrownBy(() -> disputeService.resolveDispute(
				organizerId, disputeId, new ResolveDisputeRequest(DisputeStatus.REJECTED, "reason"), false))
			.isInstanceOf(ValidationException.class);
	}

	@Test
	void resolveDispute_resolutionOpen_throwsValidation() {
		UUID disputeId = UUID.randomUUID();
		UUID organizerId = UUID.randomUUID();

		assertThatThrownBy(() -> disputeService.resolveDispute(
				organizerId, disputeId, new ResolveDisputeRequest(DisputeStatus.OPEN, "reason"), false))
			.isInstanceOf(ValidationException.class);
		verify(disputeRepository, never()).findById(any());
	}

	@Test
	void listForGroup_member_returnsDisputes() {
		UUID groupId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		when(membershipRepository.existsByGroupIdAndUserId(groupId, userId)).thenReturn(true);
		when(disputeRepository.findByGroupId(groupId))
			.thenReturn(java.util.List.of(dispute(UUID.randomUUID(), groupId, UUID.randomUUID(), userId)));

		java.util.List<DisputeResponse> result = disputeService.listForGroup(userId, groupId, false);

		assertThat(result).hasSize(1);
	}

	@Test
	void listForGroup_nonMember_throwsForbidden() {
		UUID groupId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		when(membershipRepository.existsByGroupIdAndUserId(groupId, userId)).thenReturn(false);

		assertThatThrownBy(() -> disputeService.listForGroup(userId, groupId, false))
			.isInstanceOf(ForbiddenException.class);
		verify(disputeRepository, never()).findByGroupId(any());
	}

	@Test
	void resolveDispute_admin_bypassesOrganizerCheck() {
		UUID groupId = UUID.randomUUID();
		UUID disputeId = UUID.randomUUID();
		UUID adminId = UUID.randomUUID();
		Dispute open = dispute(disputeId, groupId, UUID.randomUUID(), UUID.randomUUID());
		when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(open));
		when(disputeRepository.resolveIfOpen(eq(disputeId), eq(DisputeStatus.REJECTED), eq(adminId), any(), any(Instant.class)))
			.thenReturn(1);

		disputeService.resolveDispute(adminId, disputeId, new ResolveDisputeRequest(DisputeStatus.REJECTED, "no error found"), true);

		verify(membershipRepository, never()).findByGroupIdAndUserId(any(), any());
	}

}
