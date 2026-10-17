
package pe.edu.untels.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @NotBlank(message = "El correo electronico es obligatorio")
        @Email(message = "El correo electronico no es valido")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        String password

) {
}
