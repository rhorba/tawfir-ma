package ma.tawfir.api.group;

import java.util.UUID;
import ma.tawfir.api.group.entity.ContributionSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContributionScheduleRepository extends JpaRepository<ContributionSchedule, UUID> {
}
