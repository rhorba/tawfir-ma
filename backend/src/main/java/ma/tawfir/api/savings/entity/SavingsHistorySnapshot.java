package ma.tawfir.api.savings.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Append-only, one row per member each time a group cycle fully completes
 * (story 7.1, decisions.md 2026-07-31) — never updated in place, so a
 * member's savings history is the row set over time, not a single mutable
 * total. Feeds the Kasb export contract reserved for Phase 2.
 */
@Entity
@Table(name = "savings_history_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SavingsHistorySnapshot {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "group_id", nullable = false)
	private UUID groupId;

	@Column(name = "cycles_completed", nullable = false)
	private short cyclesCompleted;

	@Column(name = "on_time_rate", nullable = false)
	private BigDecimal onTimeRate;

	@Column(name = "disputes_involved", nullable = false)
	private short disputesInvolved;

	@Column(name = "computed_at", nullable = false)
	private Instant computedAt;

	public SavingsHistorySnapshot(UUID userId, UUID groupId, short cyclesCompleted, BigDecimal onTimeRate,
			short disputesInvolved) {
		this.userId = userId;
		this.groupId = groupId;
		this.cyclesCompleted = cyclesCompleted;
		this.onTimeRate = onTimeRate;
		this.disputesInvolved = disputesInvolved;
	}

	@PrePersist
	void onCreate() {
		this.computedAt = Instant.now();
	}

}
