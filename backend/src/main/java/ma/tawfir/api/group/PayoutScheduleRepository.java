package ma.tawfir.api.group;

import java.util.UUID;
import ma.tawfir.api.group.entity.PayoutSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayoutScheduleRepository extends JpaRepository<PayoutSchedule, UUID> {
}
