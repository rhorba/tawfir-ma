package ma.tawfir.api.savings;

import java.util.List;
import java.util.UUID;
import ma.tawfir.api.savings.entity.SavingsHistorySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavingsHistorySnapshotRepository extends JpaRepository<SavingsHistorySnapshot, UUID> {

	List<SavingsHistorySnapshot> findByUserIdOrderByComputedAtDesc(UUID userId);

}
