package pe.edu.untels.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.untels.auth.entity.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

}
