
package pe.edu.untels.forum.dto;

import java.time.OffsetDateTime;

public record PostResponse(

        Long id,

        Long authorId,

        String title,

        String content,

        String category,

        String communityName,

        String status,

        OffsetDateTime createdAt

) {
}
