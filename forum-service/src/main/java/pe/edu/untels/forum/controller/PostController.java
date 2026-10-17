
package pe.edu.untels.forum.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import pe.edu.untels.forum.dto.CreatePostRequest;
import pe.edu.untels.forum.dto.PostResponse;
import pe.edu.untels.forum.service.PostService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    // CREAR PUBLICACION CON JWT
    @PostMapping
    public ResponseEntity<Map<String, Object>> createPost(
            @Valid @RequestBody CreatePostRequest request,
            @RequestHeader(
                    value = "Authorization",
                    required = false
            ) String authorization
    ) {

        Long postId = postService.createPost(
                request,
                authorization
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Map.of(
                        "message", "Publicacion creada correctamente",
                        "postId", postId
                ));
    }

    // LISTAR PUBLICACIONES PUBLICAS
    @GetMapping
    public ResponseEntity<List<PostResponse>> getPublishedPosts() {

        List<PostResponse> posts =
                postService.getPublishedPosts();

        return ResponseEntity.ok(posts);
    }
}
