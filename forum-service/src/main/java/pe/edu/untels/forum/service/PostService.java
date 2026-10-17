
package pe.edu.untels.forum.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.untels.forum.client.AuthClient;
import pe.edu.untels.forum.dto.AuthUserResponse;
import pe.edu.untels.forum.dto.CreatePostRequest;
import pe.edu.untels.forum.dto.PostResponse;
import pe.edu.untels.forum.entity.Post;
import pe.edu.untels.forum.repository.PostRepository;

import java.util.List;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final AuthClient authClient;

    public PostService(
            PostRepository postRepository,
            AuthClient authClient
    ) {
        this.postRepository = postRepository;
        this.authClient = authClient;
    }

    // CREAR UNA PUBLICACION CON JWT
    @Transactional
    public Long createPost(
            CreatePostRequest request,
            String authorization
    ) {

        // Validar el usuario mediante Auth Service
        AuthUserResponse user =
                authClient.getAuthenticatedUser(authorization);

        // Crear la publicacion
        Post post = new Post();

        post.setAuthorId(user.id());
        post.setTitle(request.title().trim());
        post.setContent(request.content().trim());
        post.setCategory(request.category().trim());
        post.setStatus("PUBLISHED");

        // Guardar en PostgreSQL
        Post savedPost = postRepository.save(post);

        return savedPost.getId();
    }

    // LISTAR PUBLICACIONES PUBLICAS
    @Transactional(readOnly = true)
    public List<PostResponse> getPublishedPosts() {

        List<Post> posts = postRepository
                .findByStatusOrderByCreatedAtDesc("PUBLISHED");

        return posts.stream()
                .map(this::toResponse)
                .toList();
    }

    // CONVERTIR ENTIDAD A DTO
    private PostResponse toResponse(Post post) {

        return new PostResponse(
                post.getId(),
                post.getAuthorId(),
                post.getTitle(),
                post.getContent(),
                post.getCategory(),
                post.getStatus(),
                post.getCreatedAt()
        );
    }
}
