package pe.edu.untels.auth.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import pe.edu.untels.auth.service.AuthService;
import pe.edu.untels.auth.service.ResetDelivery;

@RestController
@RequestMapping("/api/v1/auth")
public class AccountActions {
    private final AuthService auth;
    private final JdbcTemplate db;
    private final PasswordEncoder passwords;
    private final ResetDelivery delivery;

    public AccountActions(
            AuthService auth, JdbcTemplate db, PasswordEncoder passwords, ResetDelivery delivery) {
        this.auth = auth;
        this.db = db;
        this.passwords = passwords;
        this.delivery = delivery;
    }

    public record EmailRequest(@NotBlank @Email @Size(max = 255) String email) {}

    public record Reset(
            @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String token,
            @NotBlank @Size(min = 8, max = 64) String password) {}

    @PostMapping("/password/request")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void request(@Valid @RequestBody EmailRequest input) {
        if (!delivery.configured())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        db.update(
                """
update users set reset_requested=now() where lower(email)=lower(?) and enabled
and reset_requested is null and (reset_sent is null or reset_sent < now()-interval '2 minutes')
""",
                input.email().strip());
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody Reset input) {
        int changed =
                db.update(
                        """
update users set password_hash=?, reset_digest=null, reset_requested=null, session_version=session_version+1
where enabled and reset_digest=? and reset_sent > now()-interval '20 minutes'
""",
                        passwords.encode(input.password()),
                        ResetDelivery.digest(input.token()));
        if (changed == 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enlace vencido o utilizado");
    }

    public record Permission(@NotBlank @Email String email, @NotNull Boolean granted) {}

    @PutMapping("/moderators")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void permission(
            @RequestHeader(value = "Authorization", required = false) String header,
            @Valid @RequestBody Permission input) {
        var admin = auth.getCurrentUser(AuthController.bearerToken(header));
        if (!"ADMIN".equals(admin.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var ids =
                db.queryForList(
                        """
update users set role=? where lower(email)=lower(?) and enabled and role=? returning id
""",
                        Long.class,
                        input.granted() ? "MODERATOR" : "USER",
                        input.email(),
                        input.granted() ? "USER" : "MODERATOR");
        if (ids.isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cuenta o rol incompatible");
        db.update(
                "insert into moderator_history(actor,account,granted) values (?,?,?)",
                admin.id(),
                ids.getFirst(),
                input.granted());
    }
}
