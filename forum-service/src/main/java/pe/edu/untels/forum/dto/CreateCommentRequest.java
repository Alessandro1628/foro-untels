
package pe.edu.untels.forum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(

        @NotBlank(message = "El comentario es obligatorio")
        @Size(
                min = 2,
                max = 5000,
                message = "El comentario debe tener entre 2 y 5000 caracteres"
        )
        String content,
        @jakarta.validation.constraints.Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String bookId

) {
    public CreateCommentRequest {
        content = content == null ? null : content.strip();
    }

}
