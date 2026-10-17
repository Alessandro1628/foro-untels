
package pe.edu.untels.forum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePostRequest(

        @NotBlank(message = "El titulo es obligatorio")
        @Size(max = 180,
                message = "El titulo no puede superar 180 caracteres")
        String title,

        @NotBlank(message = "El contenido es obligatorio")
        @Size(min = 10, max = 10000,
                message = "El contenido debe tener entre 10 y 10000 caracteres")
        String content,

        @NotBlank(message = "La categoria es obligatoria")
        @Size(max = 80,
                message = "La categoria no puede superar 80 caracteres")
        String category

) {
}
