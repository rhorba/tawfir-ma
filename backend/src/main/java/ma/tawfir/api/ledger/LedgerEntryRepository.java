package ma.tawfir.api.ledger;

import java.util.List;
import java.util.UUID;
import ma.tawfir.api.ledger.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

	List<LedgerEntry> findByGroupIdOrderByCreatedAtAsc(UUID groupId);

}
