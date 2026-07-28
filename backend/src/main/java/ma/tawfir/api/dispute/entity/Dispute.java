package ma.tawfir.api.dispute.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "disputes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Dispute {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "group_id", nullable = false)
	private UUID groupId;

	@Column(name = "ledger_entry_id", nullable = false)
	private UUID ledgerEntryId;

	@Column(name = "raised_by_user_id", nullable = false)
	private UUID raisedByUserId;

	@Column(nullable = false)
	private String reason;

	@Column(name = "evidence_note")
	private String evidenceNote;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DisputeStatus status;

	@Column(name = "resolved_by_user_id")
	private UUID resolvedByUserId;

	@Column(name = "resolution_reason")
	private String resolutionReason;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "resolved_at")
	private Instant resolvedAt;

	public Dispute(UUID groupId, UUID ledgerEntryId, UUID raisedByUserId, String reason, String evidenceNote) {
		this.groupId = groupId;
		this.ledgerEntryId = ledgerEntryId;
		this.raisedByUserId = raisedByUserId;
		this.reason = reason;
		this.evidenceNote = evidenceNote;
		this.status = DisputeStatus.OPEN;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public void resolve(DisputeStatus resolution, UUID resolverId, String resolutionReason) {
		this.status = resolution;
		this.resolvedByUserId = resolverId;
		this.resolutionReason = resolutionReason;
		this.resolvedAt = Instant.now();
	}

}
