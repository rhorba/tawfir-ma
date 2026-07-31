package ma.tawfir.api.group;

import java.util.UUID;
import ma.tawfir.api.group.entity.Group;
import ma.tawfir.api.group.entity.GroupStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupRepository extends JpaRepository<Group, UUID> {

	long countByStatus(GroupStatus status);

}
