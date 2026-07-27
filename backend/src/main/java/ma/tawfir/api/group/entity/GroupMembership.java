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
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * payoutPosition is assigned at creation time for MANUAL groups (the order of
 * the members array in the create-group request) and at finalize time for
 * RANDOMIZED groups (shuffled then assigned) — see decisions.md 2026-07-27.
 */
@Entity
@Table(name = "group_memberships")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupMembership {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "group_id", nullable = false)
	private UUID groupId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "role_in_group", nullable = false, length = 20)
	private MembershipRole roleInGroup;

	@Column(name = "payout_position")
	private Short payoutPosition;

	@Column(name = "joined_at", nullable = false)
	private Instant joinedAt;

	public GroupMembership(UUID groupId, UUID userId, MembershipRole roleInGroup, Short payoutPosition) {
		this.groupId = groupId;
		this.userId = userId;
		this.roleInGroup = roleInGroup;
		this.payoutPosition = payoutPosition;
	}

	@PrePersist
	void onCreate() {
		this.joinedAt = Instant.now();
	}

	public void assignPayoutPosition(short position) {
		this.payoutPosition = position;
	}

}
