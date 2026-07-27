package ma.tawfir.api.group.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "contribution_schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContributionSchedule {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "group_id", nullable = false)
	private UUID groupId;

	@Column(name = "cycle_number", nullable = false)
	private short cycleNumber;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "due_date", nullable = false)
	private LocalDate dueDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ContributionStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public ContributionSchedule(UUID groupId, short cycleNumber, UUID userId, LocalDate dueDate) {
		this.groupId = groupId;
		this.cycleNumber = cycleNumber;
		this.userId = userId;
		this.dueDate = dueDate;
		this.status = ContributionStatus.PENDING;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

}
