package ma.tawfir.api.group;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import ma.tawfir.api.group.entity.PayoutSchedule;
import ma.tawfir.api.group.entity.PayoutStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayoutScheduleRepository extends JpaRepository<PayoutSchedule, UUID> {

	List<PayoutSchedule> findByGroupIdOrderByCycleNumberAsc(UUID groupId);

	@Query("SELECT p FROM PayoutSchedule p WHERE p.status = ma.tawfir.api.group.entity.PayoutStatus.PENDING "
		+ "AND p.scheduledDate <= :today")
	List<PayoutSchedule> findDuePending(@Param("today") LocalDate today);

	/** Same atomic CAS pattern as ContributionScheduleRepository#compareAndSetStatus. */
	@Modifying(clearAutomatically = true)
	@Query("UPDATE PayoutSchedule p SET p.status = :to WHERE p.id = :id AND p.status IN :fromStatuses")
	int compareAndSetStatus(@Param("id") UUID id, @Param("fromStatuses") Collection<PayoutStatus> fromStatuses,
			@Param("to") PayoutStatus to);

}
