
package pe.edu.untels.forum.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import pe.edu.untels.forum.dto.CommentResponse;
import pe.edu.untels.forum.dto.CreateCommentRequest;
import pe.edu.untels.forum.service.CommentService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/posts/{postId}/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    // CREAR COMENTARIO CON JWT
    @PostMapping
    public ResponseEntity<Map<String, Object>> createComment(
            @PathVariable("postId") Long postId,
            @Valid @RequestBody CreateCommentRequest request,
            @RequestHeader(
                    value = "Authorization",
                    required = false
            ) String authorization
    ) {

        Long commentId = commentService.createComment(
                postId,
                request,
                authorization
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Map.of(
                        "message", "Comentario creado correctamente",
                        "commentId", commentId
                ));
    }

    // LISTAR COMENTARIOS DE UNA PUBLICACION
    @GetMapping
    public ResponseEntity<List<CommentResponse>> getComments(
            @PathVariable("postId") Long postId
    ) {

        List<CommentResponse> comments =
                commentService.getCommentsByPost(postId);

        return ResponseEntity.ok(comments);
    }
}
