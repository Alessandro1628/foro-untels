
package pe.edu.untels.auth.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import pe.edu.untels.auth.dto.LoginRequest;
import pe.edu.untels.auth.dto.RegisterRequest;
import pe.edu.untels.auth.dto.UserProfileResponse;
import pe.edu.untels.auth.entity.User;
import pe.edu.untels.auth.repository.UserRepository;
import pe.edu.untels.auth.security.JwtService;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    // REGISTRO
    @Transactional
    public Long register(RegisterRequest request) {

        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El correo electronico ya esta registrado"
            );
        }

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);

        user.setPasswordHash(
                passwordEncoder.encode(request.password())
        );

        user.setRole("USER");
        user.setEnabled(true);

        return userRepository.save(user).getId();
    }

    // LOGIN CON JWT
    @Transactional(readOnly = true)
    public String login(LoginRequest request) {

        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        User user = userRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Correo o contraseña incorrectos"
                ));

        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Correo o contraseña incorrectos"
            );
        }

        boolean matches = passwordEncoder.matches(
                request.password(),
                user.getPasswordHash()
        );

        if (!matches) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Correo o contraseña incorrectos"
            );
        }

        return jwtService.generateToken(user);
    }

    // OBTENER PERFIL DEL USUARIO AUTENTICADO
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUser(String token) {

        // Validar el JWT y obtener el ID
        Long userId = jwtService.validateAndGetUserId(token);

        // Buscar al usuario en PostgreSQL
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Usuario no autorizado"
                ));

        // Comprobar que la cuenta este habilitada
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Usuario no autorizado"
            );
        }

        // Devolver datos sin exponer la contraseña
        return new UserProfileResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole()
        );
    }
}
