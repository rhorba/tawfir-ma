package ma.tawfir.api.group;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import ma.tawfir.api.group.entity.ContributionSchedule;
import ma.tawfir.api.group.entity.ContributionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContributionScheduleRepository extends JpaRepository<ContributionSchedule, UUID> {

	List<ContributionSchedule> findByGroupIdOrderByCycleNumberAscUserIdAsc(UUID groupId);

	/**
	 * Atomic compare-and-swap: only transitions if the row's current status is
	 * still one of {@code fromStatuses} at UPDATE time. Returns the number of
	 * rows changed (0 or 1) so callers can distinguish "already in a different
	 * state" from success without a separate locking read
	 * (test-strategy-tawfir.md §4 "Race conditions").
	 */
	@Modifying(clearAutomatically = true)
	@Query("UPDATE ContributionSchedule c SET c.status = :to WHERE c.id = :id AND c.status IN :fromStatuses")
	int compareAndSetStatus(@Param("id") UUID id, @Param("fromStatuses") Collection<ContributionStatus> fromStatuses,
			@Param("to") ContributionStatus to);

	@Modifying(clearAutomatically = true)
	@Query("UPDATE ContributionSchedule c SET c.status = ma.tawfir.api.group.entity.ContributionStatus.LATE "
		+ "WHERE c.status = ma.tawfir.api.group.entity.ContributionStatus.PENDING AND c.dueDate < :today")
	int flagOverdueAsLate(@Param("today") LocalDate today);

	/**
	 * Admin default-rate metric (story 6.1, decisions.md 2026-07-31): counts
	 * among past-due rows only, in the specific status(es) requested.
	 */
	long countByDueDateBeforeAndStatus(LocalDate dueDate, ContributionStatus status);

	long countByDueDateBeforeAndStatusIn(LocalDate dueDate, Collection<ContributionStatus> statuses);

	@Query("SELECT DISTINCT c.groupId FROM ContributionSchedule c WHERE c.status = :status")
	List<UUID> findDistinctGroupIdsByStatus(@Param("status") ContributionStatus status);

}
