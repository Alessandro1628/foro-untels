
package pe.edu.untels.forum.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import pe.edu.untels.forum.client.AuthClient;
import pe.edu.untels.forum.dto.AuthUserResponse;
import pe.edu.untels.forum.dto.CommentResponse;
import pe.edu.untels.forum.dto.CreateCommentRequest;
import pe.edu.untels.forum.entity.Comment;
import pe.edu.untels.forum.entity.Post;
import pe.edu.untels.forum.repository.CommentRepository;
import pe.edu.untels.forum.repository.PostRepository;

import java.util.List;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final AuthClient authClient;

    public CommentService(
            CommentRepository commentRepository,
            PostRepository postRepository,
            AuthClient authClient
    ) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.authClient = authClient;
    }

    // CREAR COMENTARIO CON JWT
    @Transactional
    public Long createComment(
            Long postId,
            CreateCommentRequest request,
            String authorization
    ) {

        // 1. Validar el JWT con Auth Service
        AuthUserResponse user =
                authClient.getAuthenticatedUser(authorization);

        // 2. Verificar que la publicacion existe
        // y esta disponible publicamente
        getPublishedPost(postId);

        // 3. Validar el contenido sin espacios sobrantes
        String content = request.content().trim();

        if (content.length() < 2) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El comentario debe tener al menos 2 caracteres"
            );
        }

        // 4. Crear el comentario
        Comment comment = new Comment();

        comment.setPostId(postId);
        comment.setAuthorId(user.id());
        comment.setContent(content);
        comment.setStatus("PUBLISHED");

        // 5. Guardar en PostgreSQL
        Comment savedComment =
                commentRepository.save(comment);

        // 6. Devolver el ID del comentario
        return savedComment.getId();
    }

    // LISTAR COMENTARIOS DE UNA PUBLICACION
    @Transactional(readOnly = true)
    public List<CommentResponse> getCommentsByPost(
            Long postId
    ) {

        // Comprobar que la publicacion existe
        getPublishedPost(postId);

        // Consultar los comentarios publicados
        List<Comment> comments = commentRepository
                .findByPostIdAndStatusOrderByCreatedAtAsc(
                        postId,
                        "PUBLISHED"
                );

        // Convertir entidades a DTO
        return comments.stream()
                .map(this::toResponse)
                .toList();
    }

    // BUSCAR UNA PUBLICACION PUBLICA
    private Post getPublishedPost(Long postId) {

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Publicacion no encontrada"
                ));

        if (!"PUBLISHED".equals(post.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Publicacion no encontrada"
            );
        }

        return post;
    }

    // CONVERTIR COMMENT A COMMENTRESPONSE
    private CommentResponse toResponse(Comment comment) {

        return new CommentResponse(
                comment.getId(),
                comment.getPostId(),
                comment.getAuthorId(),
                comment.getContent(),
                comment.getStatus(),
                comment.getCreatedAt()
        );
    }
}
