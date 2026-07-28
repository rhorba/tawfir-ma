package ma.tawfir.api.dispute;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ma.tawfir.api.dispute.entity.Dispute;
import ma.tawfir.api.dispute.entity.DisputeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DisputeRepository extends JpaRepository<Dispute, UUID> {

	List<Dispute> findByGroupId(UUID groupId);

	/**
	 * Atomic compare-and-swap, same pattern as
	 * ContributionScheduleRepository#compareAndSetStatus: only resolves if the
	 * dispute is still OPEN at UPDATE time, so two concurrent resolve requests
	 * can't both succeed (test-strategy-tawfir.md §4 "Race conditions").
	 */
	@Modifying(clearAutomatically = true)
	@Query("UPDATE Dispute d SET d.status = :status, d.resolvedByUserId = :resolverId, "
		+ "d.resolutionReason = :resolutionReason, d.resolvedAt = :resolvedAt "
		+ "WHERE d.id = :id AND d.status = ma.tawfir.api.dispute.entity.DisputeStatus.OPEN")
	int resolveIfOpen(@Param("id") UUID id, @Param("status") DisputeStatus status,
			@Param("resolverId") UUID resolverId, @Param("resolutionReason") String resolutionReason,
			@Param("resolvedAt") Instant resolvedAt);

}
