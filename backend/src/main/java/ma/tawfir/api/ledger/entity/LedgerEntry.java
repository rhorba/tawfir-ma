package ma.tawfir.api.ledger.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Append-only (ADR-2) — intentionally has no setters/mutators beyond the
 * constructor. Corrections are new rows referencing this one via
 * reversalOfEntryId, never an update. Enforced beyond this app layer by a DB
 * trigger (see V5__create_ledger.sql).
 */
@Entity
@Table(name = "ledger_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerEntry {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "group_id", nullable = false)
	private UUID groupId;

	@Enumerated(EnumType.STRING)
	@Column(name = "entry_type", nullable = false, length = 20)
	private LedgerEntryType entryType;

	@Column(name = "contribution_schedule_id")
	private UUID contributionScheduleId;

	@Column(name = "payout_schedule_id")
	private UUID payoutScheduleId;

	@Column(name = "actor_user_id", nullable = false)
	private UUID actorUserId;

	@Column(nullable = false)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private LedgerSource source;

	@Column(name = "reversal_of_entry_id")
	private UUID reversalOfEntryId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public static LedgerEntry contribution(UUID groupId, UUID contributionScheduleId, UUID actorUserId,
			BigDecimal amount, LedgerSource source) {
		LedgerEntry entry = new LedgerEntry();
		entry.groupId = groupId;
		entry.entryType = LedgerEntryType.CONTRIBUTION;
		entry.contributionScheduleId = contributionScheduleId;
		entry.actorUserId = actorUserId;
		entry.amount = amount;
		entry.source = source;
		return entry;
	}

	public static LedgerEntry payout(UUID groupId, UUID payoutScheduleId, UUID actorUserId,
			BigDecimal amount, LedgerSource source) {
		LedgerEntry entry = new LedgerEntry();
		entry.groupId = groupId;
		entry.entryType = LedgerEntryType.PAYOUT;
		entry.payoutScheduleId = payoutScheduleId;
		entry.actorUserId = actorUserId;
		entry.amount = amount;
		entry.source = source;
		return entry;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

}
