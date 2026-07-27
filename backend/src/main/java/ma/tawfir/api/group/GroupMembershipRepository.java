package ma.tawfir.api.group;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ma.tawfir.api.group.entity.GroupMembership;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupMembershipRepository extends JpaRepository<GroupMembership, UUID> {

	List<GroupMembership> findByGroupId(UUID groupId);

	List<GroupMembership> findByUserId(UUID userId);

	Optional<GroupMembership> findByGroupIdAndUserId(UUID groupId, UUID userId);

	boolean existsByGroupIdAndUserId(UUID groupId, UUID userId);

}
