package ma.tawfir.api.group.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "groups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Group {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(nullable = false)
	private String name;

	@Column(name = "organizer_id", nullable = false)
	private UUID organizerId;

	@Column(name = "contribution_amount", nullable = false)
	private BigDecimal contributionAmount;

	@Column(nullable = false, length = 3)
	private String currency;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Frequency frequency;

	@Column(name = "total_cycles", nullable = false)
	private short totalCycles;

	@Enumerated(EnumType.STRING)
	@Column(name = "payout_order_mode", nullable = false, length = 20)
	private PayoutOrderMode payoutOrderMode;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private GroupStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public Group(String name, UUID organizerId, BigDecimal contributionAmount, Frequency frequency,
			short totalCycles, PayoutOrderMode payoutOrderMode) {
		this.name = name;
		this.organizerId = organizerId;
		this.contributionAmount = contributionAmount;
		this.currency = "MAD";
		this.frequency = frequency;
		this.totalCycles = totalCycles;
		this.payoutOrderMode = payoutOrderMode;
		this.status = GroupStatus.DRAFT;
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	public boolean isDraft() {
		return status == GroupStatus.DRAFT;
	}

	public void activate() {
		this.status = GroupStatus.ACTIVE;
	}

}
