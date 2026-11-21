
package pe.edu.untels.forum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.untels.forum.entity.Post;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PostRepository extends JpaRepository<Post, Long> {

    // Obtener publicaciones publicadas, de la más reciente a la más antigua
    List<Post> findByStatusOrderByCreatedAtDesc(String status);

    // Obtener publicaciones de un estudiante
    List<Post> findByAuthorIdOrderByCreatedAtDesc(Long authorId);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from Post item where item.id = :id")
    Optional<Post> lockById(Long id);


    @Query("""
        select p from Post p left join fetch p.community where p.status = 'PUBLISHED' and p.moderatedAt is null
        and (p.withdrawnAt is null or exists (
            select c.id from Comment c where c.postId = p.id and c.status = 'PUBLISHED'
            and c.withdrawnAt is null and c.moderatedAt is null))
        order by p.createdAt desc, p.id desc
        """)
    List<Post> visibleFeed();

}
