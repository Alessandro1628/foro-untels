
package pe.edu.untels.forum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.untels.forum.entity.Comment;

import java.util.List;

public interface CommentRepository
        extends JpaRepository<Comment, Long> {

    // Obtener comentarios publicados de una publicacion
    // Ordenados del mas antiguo al mas reciente
    List<Comment> findByPostIdAndStatusOrderByCreatedAtAsc(
            Long postId,
            String status
    );

    // Obtener comentarios realizados por un estudiante
    List<Comment> findByAuthorIdOrderByCreatedAtDesc(
            Long authorId
    );
}
