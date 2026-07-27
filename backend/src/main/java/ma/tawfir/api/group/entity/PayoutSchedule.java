package ma.tawfir.api.group.entity;

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
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payout_schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayoutSchedule {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "group_id", nullable = false)
	private UUID groupId;

	@Column(name = "cycle_number", nullable = false)
	private short cycleNumber;

	@Column(name = "recipient_id", nullable = false)
	private UUID recipientId;

	@Column(name = "scheduled_date", nullable = false)
	private LocalDate scheduledDate;

	@Column(nullable = false)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PayoutStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public PayoutSchedule(UUID groupId, short cycleNumber, UUID recipientId, LocalDate scheduledDate, BigDecimal amount) {
		this.groupId = groupId;
		this.cycleNumber = cycleNumber;
		this.recipientId = recipientId;
		this.scheduledDate = scheduledDate;
		this.amount = amount;
		this.status = PayoutStatus.PENDING;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

}
