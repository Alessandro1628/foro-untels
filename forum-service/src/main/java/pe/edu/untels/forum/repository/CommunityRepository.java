package pe.edu.untels.forum.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.untels.forum.entity.Community;

public interface CommunityRepository extends JpaRepository<Community, Long> {
    List<Community> findAllByOrderByNameAsc();
}
