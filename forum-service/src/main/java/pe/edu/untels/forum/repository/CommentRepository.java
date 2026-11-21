
package pe.edu.untels.forum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.untels.forum.entity.Comment;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CommentRepository
        extends JpaRepository<Comment, Long> {

    // Obtener comentarios publicados de una publicacion
    // Ordenados del mas antiguo al mas reciente
    List<Comment> findByPostIdAndStatusAndWithdrawnAtIsNullAndModeratedAtIsNullOrderByCreatedAtAscIdAsc(
            Long postId,
            String status
    );

    // Obtener comentarios realizados por un estudiante
    List<Comment> findByAuthorIdOrderByCreatedAtDesc(
            Long authorId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from Comment item where item.id = :id")
    Optional<Comment> lockById(Long id);

}
