
package pe.edu.untels.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "El nombre completo es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres")
        String fullName,

        @NotBlank(message = "El correo electronico es obligatorio")
        @Email(message = "El correo electronico no es valido")
        @Size(max = 255, message = "El correo es demasiado largo")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 64,
                message = "La contraseña debe tener entre 8 y 64 caracteres")
        String password

) {
}
