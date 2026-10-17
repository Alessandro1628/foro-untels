
package pe.edu.untels.auth.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import pe.edu.untels.auth.dto.LoginRequest;
import pe.edu.untels.auth.dto.RegisterRequest;
import pe.edu.untels.auth.dto.UserProfileResponse;
import pe.edu.untels.auth.service.AuthService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // REGISTRO
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        Long userId = authService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Map.of(
                        "message", "Usuario registrado correctamente",
                        "userId", userId
                ));
    }

    // LOGIN
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        String token = authService.login(request);

        return ResponseEntity.ok(Map.of(
                "message", "Inicio de sesion correcto",
                "accessToken", token,
                "tokenType", "Bearer"
        ));
    }

    // PERFIL PROTEGIDO CON JWT
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> me(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            ) String authorization
    ) {

        if (authorization == null ||
                !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Token de acceso requerido"
            );
        }

        String token = authorization.substring(7).trim();

        UserProfileResponse profile =
                authService.getCurrentUser(token);

        return ResponseEntity.ok(profile);
    }
}
