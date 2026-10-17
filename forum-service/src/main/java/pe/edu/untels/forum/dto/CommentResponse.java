
package pe.edu.untels.forum.dto;

import java.time.OffsetDateTime;

public record CommentResponse(

        Long id,

        Long postId,

        Long authorId,

        String content,

        String status,

        OffsetDateTime createdAt

) {
}
