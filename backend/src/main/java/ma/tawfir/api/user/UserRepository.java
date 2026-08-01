package ma.tawfir.api.user;

import java.util.Optional;
import java.util.UUID;
import ma.tawfir.api.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByPhoneNumberHash(String phoneNumberHash);

}
