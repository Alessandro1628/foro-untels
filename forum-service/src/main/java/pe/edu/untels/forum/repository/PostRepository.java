
package pe.edu.untels.forum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.untels.forum.entity.Post;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    // Obtener publicaciones publicadas, de la más reciente a la más antigua
    List<Post> findByStatusOrderByCreatedAtDesc(String status);

    // Obtener publicaciones de un estudiante
    List<Post> findByAuthorIdOrderByCreatedAtDesc(Long authorId);

}
