
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
import pe.edu.untels.auth.dto.UpdateProfileRequest;

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

        try {
            return userRepository.saveAndFlush(user).getId();
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            if (ex.getMostSpecificCause() instanceof java.sql.SQLException sql && "23505".equals(sql.getSQLState())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "El correo electrónico ya está registrado");
            }
            throw ex;
        }
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

        return profileOf(authenticatedUser(token));
    }

    @Transactional
    public UserProfileResponse updateProfile(String token, UpdateProfileRequest request) {
        User user = authenticatedUser(token);
        user.setFullName(request.fullName().strip());
        user.setAcademicProgram(request.academicProgram() == null ? null : request.academicProgram().strip());
        user.setBiography(request.biography() == null ? null : request.biography().strip());
        return profileOf(user);
    }

    private User authenticatedUser(String token) {
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

        return user;
    }

    private UserProfileResponse profileOf(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getAcademicProgram(),
                user.getBiography()
        );
    }
}
