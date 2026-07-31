package ma.tawfir.api.savings;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import ma.tawfir.api.dispute.DisputeRepository;
import ma.tawfir.api.dispute.entity.Dispute;
import ma.tawfir.api.group.ContributionScheduleRepository;
import ma.tawfir.api.group.GroupMembershipRepository;
import ma.tawfir.api.group.entity.ContributionSchedule;
import ma.tawfir.api.group.entity.ContributionStatus;
import ma.tawfir.api.group.entity.GroupMembership;
import ma.tawfir.api.ledger.LedgerEntryRepository;
import ma.tawfir.api.ledger.entity.LedgerEntry;
import ma.tawfir.api.savings.dto.SavingsHistoryResponse;
import ma.tawfir.api.savings.entity.SavingsHistorySnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records a savings-history snapshot per member whenever a group cycle fully
 * completes (story 7.1). "Cycle complete" is a proxy for real payout
 * execution (Epic 4 doesn't exist yet, blocked on SDR-3, decisions.md
 * 2026-07-31) — every member's contribution for the cycle reaching CONFIRMED
 * is the closest available signal.
 */
@Service
public class SavingsHistoryService {

	private static final int RATE_SCALE = 2;

	private final SavingsHistorySnapshotRepository snapshotRepository;
	private final ContributionScheduleRepository contributionScheduleRepository;
	private final GroupMembershipRepository membershipRepository;
	private final LedgerEntryRepository ledgerEntryRepository;
	private final DisputeRepository disputeRepository;

	public SavingsHistoryService(SavingsHistorySnapshotRepository snapshotRepository,
			ContributionScheduleRepository contributionScheduleRepository,
			GroupMembershipRepository membershipRepository, LedgerEntryRepository ledgerEntryRepository,
			DisputeRepository disputeRepository) {
		this.snapshotRepository = snapshotRepository;
		this.contributionScheduleRepository = contributionScheduleRepository;
		this.membershipRepository = membershipRepository;
		this.ledgerEntryRepository = ledgerEntryRepository;
		this.disputeRepository = disputeRepository;
	}

	@Transactional
	public void recordSnapshotsIfCycleComplete(UUID groupId, short cycleNumber) {
		List<ContributionSchedule> allSchedules =
			contributionScheduleRepository.findByGroupIdOrderByCycleNumberAscUserIdAsc(groupId);

		boolean cycleComplete = allSchedules.stream()
			.filter(s -> s.getCycleNumber() == cycleNumber)
			.allMatch(s -> s.getStatus() == ContributionStatus.CONFIRMED);
		if (!cycleComplete) {
			return;
		}

		Map<UUID, Long> disputesByUser = disputesByPayer(groupId, allSchedules);

		for (GroupMembership member : membershipRepository.findByGroupId(groupId)) {
			UUID userId = member.getUserId();
			List<ContributionSchedule> confirmed = allSchedules.stream()
				.filter(s -> s.getUserId().equals(userId) && s.getStatus() == ContributionStatus.CONFIRMED)
				.toList();

			short cyclesCompleted = (short) confirmed.size();
			long onTimeCount = confirmed.stream().filter(s -> !s.isWasLate()).count();
			BigDecimal onTimeRate = cyclesCompleted == 0
				? BigDecimal.ZERO.setScale(RATE_SCALE, RoundingMode.HALF_UP)
				: BigDecimal.valueOf(onTimeCount * 100.0 / cyclesCompleted).setScale(RATE_SCALE, RoundingMode.HALF_UP);
			short disputesInvolved = disputesByUser.getOrDefault(userId, 0L).shortValue();

			snapshotRepository.save(
				new SavingsHistorySnapshot(userId, groupId, cyclesCompleted, onTimeRate, disputesInvolved));
		}
	}

	public List<SavingsHistoryResponse> getHistoryForUser(UUID userId) {
		return snapshotRepository.findByUserIdOrderByComputedAtDesc(userId).stream()
			.map(s -> new SavingsHistoryResponse(
				s.getGroupId(), s.getCyclesCompleted(), s.getOnTimeRate(), s.getDisputesInvolved(), s.getComputedAt()))
			.toList();
	}

	/** Counts disputes per the userId who owed the disputed contribution (Dispute -> LedgerEntry -> ContributionSchedule). */
	private Map<UUID, Long> disputesByPayer(UUID groupId, List<ContributionSchedule> allSchedules) {
		Map<UUID, UUID> payerByScheduleId = allSchedules.stream()
			.collect(Collectors.toMap(ContributionSchedule::getId, ContributionSchedule::getUserId));
		Map<UUID, UUID> scheduleIdByLedgerEntryId = ledgerEntryRepository.findByGroupIdOrderByCreatedAtAsc(groupId).stream()
			.filter(e -> e.getContributionScheduleId() != null)
			.collect(Collectors.toMap(LedgerEntry::getId, LedgerEntry::getContributionScheduleId));

		return disputeRepository.findByGroupId(groupId).stream()
			.map(Dispute::getLedgerEntryId)
			.map(scheduleIdByLedgerEntryId::get)
			.filter(Objects::nonNull)
			.map(payerByScheduleId::get)
			.filter(Objects::nonNull)
			.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
	}

}
