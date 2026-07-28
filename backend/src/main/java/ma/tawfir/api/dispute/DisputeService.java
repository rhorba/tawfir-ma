package ma.tawfir.api.dispute;

import java.time.Instant;
import java.util.List;
import java.util.Set;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opening a dispute is scoped to CONFIRMED contributions only (see
 * decisions.md 2026-07-28) — that's the only state with a ledger entry to
 * dispute against. Resolving uses the same CAS pattern as
 * ContributionService (DisputeRepository#resolveIfOpen) so concurrent
 * resolve attempts can't both succeed.
 */
@Service
public class DisputeService {

	private static final Set<ContributionStatus> DISPUTABLE_STATUSES = Set.of(ContributionStatus.CONFIRMED);

	private final DisputeRepository disputeRepository;
	private final LedgerEntryRepository ledgerEntryRepository;
	private final ContributionScheduleRepository contributionScheduleRepository;
	private final GroupMembershipRepository membershipRepository;

	public DisputeService(DisputeRepository disputeRepository, LedgerEntryRepository ledgerEntryRepository,
			ContributionScheduleRepository contributionScheduleRepository,
			GroupMembershipRepository membershipRepository) {
		this.disputeRepository = disputeRepository;
		this.ledgerEntryRepository = ledgerEntryRepository;
		this.contributionScheduleRepository = contributionScheduleRepository;
		this.membershipRepository = membershipRepository;
	}

	@Transactional
	public DisputeResponse openDispute(UUID actingUserId, UUID groupId, OpenDisputeRequest request, boolean isAdmin) {
		if (!isAdmin && !membershipRepository.existsByGroupIdAndUserId(groupId, actingUserId)) {
			throw new ForbiddenException("You are not a member of this group");
		}

		LedgerEntry ledgerEntry = ledgerEntryRepository.findById(request.ledgerEntryId())
			.orElseThrow(() -> new NotFoundException("Ledger entry not found: " + request.ledgerEntryId()));
		if (!ledgerEntry.getGroupId().equals(groupId)) {
			throw new NotFoundException("Ledger entry not found: " + request.ledgerEntryId());
		}
		UUID scheduleId = ledgerEntry.getContributionScheduleId();
		if (scheduleId == null) {
			throw new ValidationException("Only contribution ledger entries can be disputed");
		}

		int updated = contributionScheduleRepository.compareAndSetStatus(
			scheduleId, DISPUTABLE_STATUSES, ContributionStatus.DISPUTED);
		if (updated == 0) {
			throw new ValidationException("Contribution is not in a disputable state");
		}

		Dispute dispute = new Dispute(groupId, request.ledgerEntryId(), actingUserId, request.reason(), request.evidenceNote());
		return toResponse(disputeRepository.save(dispute));
	}

	@Transactional
	public DisputeResponse resolveDispute(UUID actingUserId, UUID disputeId, ResolveDisputeRequest request, boolean isAdmin) {
		if (request.resolution() == DisputeStatus.OPEN) {
			throw new ValidationException("Resolution must be ACCEPTED or REJECTED");
		}
		Dispute dispute = disputeRepository.findById(disputeId)
			.orElseThrow(() -> new NotFoundException("Dispute not found: " + disputeId));

		if (!isAdmin) {
			requireOrganizer(dispute.getGroupId(), actingUserId);
		}

		int updated = disputeRepository.resolveIfOpen(
			disputeId, request.resolution(), actingUserId, request.resolutionReason(), Instant.now());
		if (updated == 0) {
			throw new ValidationException("Dispute has already been resolved");
		}

		return toResponse(disputeRepository.findById(disputeId)
			.orElseThrow(() -> new NotFoundException("Dispute not found: " + disputeId)));
	}

	public List<DisputeResponse> listForGroup(UUID actingUserId, UUID groupId, boolean isAdmin) {
		if (!isAdmin && !membershipRepository.existsByGroupIdAndUserId(groupId, actingUserId)) {
			throw new ForbiddenException("You are not a member of this group");
		}
		return disputeRepository.findByGroupId(groupId).stream()
			.map(this::toResponse)
			.toList();
	}

	private void requireOrganizer(UUID groupId, UUID userId) {
		GroupMembership membership = membershipRepository.findByGroupIdAndUserId(groupId, userId)
			.orElseThrow(() -> new ForbiddenException("You are not a member of this group"));
		if (membership.getRoleInGroup() != MembershipRole.ORGANIZER) {
			throw new ForbiddenException("Only the group organizer can resolve a dispute");
		}
	}

	private DisputeResponse toResponse(Dispute dispute) {
		return new DisputeResponse(dispute.getId(), dispute.getGroupId(), dispute.getLedgerEntryId(),
			dispute.getRaisedByUserId(), dispute.getReason(), dispute.getEvidenceNote(), dispute.getStatus(),
			dispute.getResolvedByUserId(), dispute.getResolutionReason(), dispute.getCreatedAt(), dispute.getResolvedAt());
	}

}
