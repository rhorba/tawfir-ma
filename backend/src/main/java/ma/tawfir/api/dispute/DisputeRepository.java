package ma.tawfir.api.dispute;

import java.util.List;
import java.util.UUID;
import ma.tawfir.api.dispute.entity.Dispute;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisputeRepository extends JpaRepository<Dispute, UUID> {

	List<Dispute> findByGroupId(UUID groupId);

}
