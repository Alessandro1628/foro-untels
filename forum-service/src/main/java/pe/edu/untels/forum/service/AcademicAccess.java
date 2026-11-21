package pe.edu.untels.forum.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import pe.edu.untels.forum.client.AuthClient;
import pe.edu.untels.forum.dto.AuthUserResponse;

import java.util.List;

@Component
public class AcademicAccess {
    private final AuthClient auth;
    private final JdbcTemplate db;

    public AcademicAccess(AuthClient auth, JdbcTemplate db) {
        this.auth = auth;
        this.db = db;
    }

    public AuthUserResponse user(String header) {
        return auth.getAuthenticatedUser(header);
    }

    public AuthUserResponse reviewer(String header, boolean allowModerator) {
        var user = user(header);
        if (!"ADMIN".equals(user.role()) && !(allowModerator && "MODERATOR".equals(user.role())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return user;
    }

    public void activeSubject(long id) {
        if (db.queryForList(
                        "select id from subjects where active and id=? for share", Long.class, id)
                .isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Asignatura no disponible");
    }

    public static void changed(int count) {
        if (count == 0)
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Registro no disponible para esta operación");
    }

    public static long requiredId(List<Long> ids) {
        if (ids.isEmpty()) throw ForumAccess.missing();
        return ids.getFirst();
    }
}
