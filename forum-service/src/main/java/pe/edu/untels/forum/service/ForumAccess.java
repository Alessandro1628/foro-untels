package pe.edu.untels.forum.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.untels.forum.entity.Comment;
import pe.edu.untels.forum.entity.Post;
import pe.edu.untels.forum.repository.CommentRepository;
import pe.edu.untels.forum.repository.PostRepository;

@Component
public class ForumAccess {
    private final PostRepository posts;
    private final CommentRepository comments;

    public ForumAccess(PostRepository posts, CommentRepository comments) {
        this.posts = posts;
        this.comments = comments;
    }

    // All mutations lock the thread before the comment, including moderator decisions.
    public Post thread(Long id, boolean lock) {
        Post post = (lock ? posts.lockById(id) : posts.findById(id)).orElseThrow(ForumAccess::missing);
        if (!"PUBLISHED".equals(post.getStatus()) || post.isModerated()) throw missing();
        return post;
    }

    public Comment comment(Long postId, Long commentId) {
        return comments.lockById(commentId)
                .filter(c -> postId.equals(c.getPostId()) && "PUBLISHED".equals(c.getStatus()))
                .filter(c -> !c.isModerated() && !c.isWithdrawn()).orElseThrow(ForumAccess::missing);
    }

    public static void requireOwner(Long owner, Long actor) {
        if (!owner.equals(actor)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el autor puede modificar su aporte");
    }

    public static void requireOpen(Post post) {
        if (post.isWithdrawn()) throw new ResponseStatusException(HttpStatus.CONFLICT, "La consulta fue retirada");
    }

    public static ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Contenido no disponible");
    }
}
