package ma.tawfir.api.group;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import ma.tawfir.api.common.ForbiddenException;
import ma.tawfir.api.common.NotFoundException;
import ma.tawfir.api.common.ValidationException;
import ma.tawfir.api.group.dto.PayoutResponse;
import ma.tawfir.api.group.entity.ContributionStatus;
import ma.tawfir.api.group.entity.GroupMembership;
import ma.tawfir.api.group.entity.MembershipRole;
import ma.tawfir.api.group.entity.PayoutSchedule;
import ma.tawfir.api.group.entity.PayoutStatus;
import ma.tawfir.api.ledger.LedgerEntryRepository;
import ma.tawfir.api.ledger.entity.LedgerEntry;
import ma.tawfir.api.ledger.entity.LedgerSource;
import ma.tawfir.api.payment.CmiClient;
import ma.tawfir.api.savings.SavingsHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payout execution has three entry points that converge on the same
 * CAS-then-ledger pattern used by ContributionService: the scheduled cron
 * (story 4.1), an organizer's manual override (4.2), and a CMI webhook
 * confirming money actually moved (4.3). A CMI transfer failure moves the
 * payout to FAILED (no ledger entry) so an organizer can retry via manual
 * override, rather than leaving it silently stuck (decisions.md 2026-07-31).
 */
@Service
public class PayoutScheduleService {

	private static final Logger log = LoggerFactory.getLogger(PayoutScheduleService.class);
	private static final Set<PayoutStatus> AUTO_EXECUTABLE_STATUSES = Set.of(PayoutStatus.PENDING);
	private static final Set<PayoutStatus> OVERRIDE_FROM_STATUSES = Set.of(PayoutStatus.PENDING, PayoutStatus.FAILED);
	private static final Set<PayoutStatus> WEBHOOK_EXECUTABLE_STATUSES = Set.of(PayoutStatus.PENDING, PayoutStatus.FAILED);

	private final PayoutScheduleRepository payoutScheduleRepository;
	private final ContributionScheduleRepository contributionScheduleRepository;
	private final GroupMembershipRepository membershipRepository;
	private final LedgerEntryRepository ledgerEntryRepository;
	private final CmiClient cmiClient;
	private final SavingsHistoryService savingsHistoryService;

	public PayoutScheduleService(PayoutScheduleRepository payoutScheduleRepository,
			ContributionScheduleRepository contributionScheduleRepository,
			GroupMembershipRepository membershipRepository, LedgerEntryRepository ledgerEntryRepository,
			CmiClient cmiClient, SavingsHistoryService savingsHistoryService) {
		this.payoutScheduleRepository = payoutScheduleRepository;
		this.contributionScheduleRepository = contributionScheduleRepository;
		this.membershipRepository = membershipRepository;
		this.ledgerEntryRepository = ledgerEntryRepository;
		this.cmiClient = cmiClient;
		this.savingsHistoryService = savingsHistoryService;
	}

	/**
	 * Story 4.1: called by PayoutScheduler for each PENDING payout past its
	 * scheduled_date. Returns false (no-op, stays PENDING) if any contribution
	 * for that group+cycle isn't CONFIRMED yet.
	 */
	@Transactional
	public boolean executeIfReady(PayoutSchedule payout) {
		long unconfirmed = contributionScheduleRepository.countByGroupIdAndCycleNumberAndStatusNot(
			payout.getGroupId(), payout.getCycleNumber(), ContributionStatus.CONFIRMED);
		if (unconfirmed > 0) {
			return false;
		}
		transferAndTransition(payout.getId(), AUTO_EXECUTABLE_STATUSES, PayoutStatus.EXECUTED,
			LedgerSource.SYSTEM_SCHEDULED, payout.getRecipientId());
		return true;
	}

	/** Story 4.2: organizer-triggered, for when auto-execution didn't fire. */
	@Transactional
	public PayoutResponse executeManualOverride(UUID actingUserId, UUID groupId, UUID payoutId) {
		requireOrganizer(groupId, actingUserId);
		requirePayout(groupId, payoutId);
		transferAndTransition(payoutId, OVERRIDE_FROM_STATUSES, PayoutStatus.MANUAL_OVERRIDE,
			LedgerSource.ORGANIZER_CONFIRMED, actingUserId);
		return toResponse(requirePayout(groupId, payoutId));
	}

	/**
	 * Story 4.3: a signed CMI webhook confirming money actually moved. If we
	 * already executed the payout ourselves (EXECUTED/MANUAL_OVERRIDE), this is
	 * a no-op — the ledger entry already exists. Otherwise a real payment
	 * confirmation is stronger evidence than our own scheduling decision, so
	 * the webhook itself drives PENDING/FAILED -> EXECUTED.
	 */
	@Transactional
	public void confirmViaWebhook(UUID payoutId, BigDecimal reportedAmount) {
		PayoutSchedule payout = requirePayoutById(payoutId);
		if (payout.getStatus() == PayoutStatus.EXECUTED || payout.getStatus() == PayoutStatus.MANUAL_OVERRIDE) {
			return;
		}
		if (reportedAmount.compareTo(payout.getAmount()) != 0) {
			throw new ValidationException("Webhook amount does not match the scheduled payout amount");
		}
		transferAndTransition(payoutId, WEBHOOK_EXECUTABLE_STATUSES, PayoutStatus.EXECUTED,
			LedgerSource.CMI_WEBHOOK, payout.getRecipientId());
	}

	public List<PayoutResponse> listForGroup(UUID actingUserId, UUID groupId, boolean isAdmin) {
		if (!isAdmin && !membershipRepository.existsByGroupIdAndUserId(groupId, actingUserId)) {
			throw new ForbiddenException("You are not a member of this group");
		}
		return payoutScheduleRepository.findByGroupIdOrderByCycleNumberAsc(groupId).stream()
			.map(this::toResponse)
			.toList();
	}

	private void transferAndTransition(UUID payoutId, Set<PayoutStatus> fromStatuses, PayoutStatus toStatus,
			LedgerSource source, UUID actorUserId) {
		PayoutSchedule payout = requirePayoutById(payoutId);
		try {
			cmiClient.initiateTransfer(payoutId.toString(), payout.getAmount());
		} catch (RuntimeException e) {
			payoutScheduleRepository.compareAndSetStatus(payoutId, fromStatuses, PayoutStatus.FAILED);
			log.warn("CMI transfer failed for payout {}", payoutId, e);
			throw e;
		}

		int updated = payoutScheduleRepository.compareAndSetStatus(payoutId, fromStatuses, toStatus);
		if (updated == 0) {
			return; // raced with another execution path that already landed
		}
		ledgerEntryRepository.save(
			LedgerEntry.payout(payout.getGroupId(), payoutId, actorUserId, payout.getAmount(), source));

		// story 7.1: a cycle is "complete" once its payout has actually executed, not merely
		// once contributions are confirmed (decisions.md 2026-08-03 — Epic 4 landed, so the
		// old contribution-confirmed proxy is no longer the strongest available signal).
		savingsHistoryService.recordSnapshotsIfCycleComplete(payout.getGroupId(), payout.getCycleNumber());
	}

	private void requireOrganizer(UUID groupId, UUID userId) {
		GroupMembership membership = membershipRepository.findByGroupIdAndUserId(groupId, userId)
			.orElseThrow(() -> new ForbiddenException("You are not a member of this group"));
		if (membership.getRoleInGroup() != MembershipRole.ORGANIZER) {
			throw new ForbiddenException("Only the group organizer can execute a payout");
		}
	}

	private PayoutSchedule requirePayout(UUID groupId, UUID payoutId) {
		PayoutSchedule payout = requirePayoutById(payoutId);
		if (!payout.getGroupId().equals(groupId)) {
			throw new NotFoundException("Payout not found: " + payoutId);
		}
		return payout;
	}

	private PayoutSchedule requirePayoutById(UUID payoutId) {
		return payoutScheduleRepository.findById(payoutId)
			.orElseThrow(() -> new NotFoundException("Payout not found: " + payoutId));
	}

	private PayoutResponse toResponse(PayoutSchedule payout) {
		return new PayoutResponse(payout.getId(), payout.getGroupId(), payout.getCycleNumber(),
			payout.getRecipientId(), payout.getScheduledDate(), payout.getAmount(), payout.getStatus());
	}

}
