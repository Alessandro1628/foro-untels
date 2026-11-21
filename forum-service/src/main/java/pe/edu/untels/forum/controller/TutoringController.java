package pe.edu.untels.forum.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import pe.edu.untels.forum.service.AcademicAccess;

import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/academic")
public class TutoringController {
    private final JdbcTemplate db;
    private final AcademicAccess access;

    public TutoringController(JdbcTemplate db, AcademicAccess access) {
        this.db = db;
        this.access = access;
    }

    public enum Kind {
        TEACHER,
        TUTOR
    }

    public record Application(
            @NotNull Kind kind, Long subject, @NotBlank @Size(max = 1500) String evidence) {}

    @PostMapping("/credentials")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public long apply(
            @RequestBody @Valid Application input,
            @RequestHeader(value = "Authorization", required = false) String header) {
        var user = access.user(header);
        if ((input.kind() == Kind.TUTOR) != (input.subject() != null))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        if (input.subject() != null) access.activeSubject(input.subject());
        return db.queryForObject(
                """
insert into credentials(account,full_name,kind,subject_id,evidence) values (?,?,?,?,?) returning id
""",
                Long.class,
                user.id(),
                user.fullName(),
                input.kind().name(),
                input.subject(),
                input.evidence().strip());
    }

    private static final String APPLICATIONS =
            """
            select c.id,c.kind,c.full_name,c.evidence,c.status,c.subject_id,s.name as subject
            from credentials c left join subjects s on s.id=c.subject_id
            """;

    @GetMapping("/credentials/mine")
    public List<Map<String, Object>> mine(
            @RequestHeader(value = "Authorization", required = false) String header) {
        return db.queryForList(
                APPLICATIONS + " where c.account=? order by c.id desc", access.user(header).id());
    }

    @GetMapping("/credentials/pending")
    public List<Map<String, Object>> waiting(
            @RequestHeader(value = "Authorization", required = false) String header) {
        access.reviewer(header, false);
        return db.queryForList(APPLICATIONS + " where c.status='PENDING' order by c.id");
    }

    @PutMapping("/credentials/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decide(
            @PathVariable long id,
            @Valid @RequestBody DirectoryController.Decision input,
            @RequestHeader(value = "Authorization", required = false) String header) {
        var admin = access.reviewer(header, false);
        AcademicAccess.changed(
                db.update(
                        "update credentials set status=?,reviewer=? where id=? and"
                            + " status='PENDING'",
                        input.approve() ? "APPROVED" : "REJECTED",
                        admin.id(),
                        id));
    }

    @GetMapping("/tutors")
    public List<Map<String, Object>> tutors(@RequestParam(required = false) Long subject) {
        String sql =
                """
select c.id,c.full_name,s.name as subject from credentials c join subjects s on s.id=c.subject_id
where c.status='APPROVED' and c.kind='TUTOR' and s.active
""";
        return subject == null
                ? db.queryForList(sql + " order by c.full_name")
                : db.queryForList(sql + " and s.id=? order by c.full_name", subject);
    }

    public record Slot(
            @Positive long credential,
            @NotNull @Future OffsetDateTime start,
            @Min(10) @Max(240) int minutes) {}

    @PostMapping("/slots")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public long publish(
            @RequestBody @Valid Slot input,
            @RequestHeader(value = "Authorization", required = false) String header) {
        var tutor = access.user(header);
        // Serializa los horarios de un tutor aun cuando enseña varias asignaturas.
        db.queryForList("select pg_advisory_xact_lock(?)", tutor.id());
        var end = input.start().plusMinutes(input.minutes());
        if (db.queryForObject(
                "select exists(select from tutoring_slots where tutor_id=? and starts_at<? and"
                    + " ends_at>?)",
                Boolean.class,
                tutor.id(),
                end,
                input.start())) throw new ResponseStatusException(HttpStatus.CONFLICT);
        return AcademicAccess.requiredId(
                db.queryForList(
                        """
insert into tutoring_slots(credential_id,tutor_id,tutor_email,starts_at,ends_at)
select c.id,c.account,?,?,? from credentials c join subjects s on s.id=c.subject_id
where c.id=? and c.account=? and c.status='APPROVED' and c.kind='TUTOR' and s.active returning id
""",
                        Long.class,
                        tutor.email(),
                        input.start(),
                        end,
                        input.credential(),
                        tutor.id()));
    }

    @GetMapping("/slots")
    public List<Map<String, Object>> available(@RequestParam long credential) {
        return db.queryForList(
                """
select t.id,t.starts_at,t.ends_at from tutoring_slots t join credentials c on c.id=t.credential_id
join subjects s on s.id=c.subject_id where t.credential_id=? and t.starts_at>now() and student_id is null
and c.status='APPROVED' and s.active order by starts_at
""",
                credential);
    }

    @PutMapping("/slots/{id}/reserve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reserve(
            @PathVariable long id,
            @RequestHeader(value = "Authorization", required = false) String header) {
        var user = access.user(header);
        AcademicAccess.changed(
                db.update(
                        """
update tutoring_slots t set student_id=?,student_email=? from credentials c join subjects s on c.subject_id=s.id
where t.credential_id=c.id and c.status='APPROVED' and s.active and t.id=? and t.tutor_id<>?
and t.starts_at>now() and t.student_id is null
""",
                        user.id(),
                        user.email(),
                        id,
                        user.id()));
    }

    @GetMapping("/sessions")
    public List<Map<String, Object>> sessions(
            @RequestHeader(value = "Authorization", required = false) String header) {
        var user = access.user(header);
        return db.queryForList(
                """
select t.id,starts_at,ends_at,s.name as subject,c.full_name as tutor,tutor_id,student_id,meet_url,sync_state,rating
from tutoring_slots t join credentials c on c.id=t.credential_id join subjects s on s.id=c.subject_id
where student_id is not null and (student_id=? or tutor_id=?) order by starts_at desc
""",
                user.id(),
                user.id());
    }

    public record Rating(@Min(1) @Max(5) int stars) {}

    @PutMapping("/sessions/{id}/rating")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rate(
            @PathVariable long id,
            @Valid @RequestBody Rating input,
            @RequestHeader(value = "Authorization", required = false) String header) {
        AcademicAccess.changed(
                db.update(
                        "update tutoring_slots set rating=? where id=? and student_id=? and"
                            + " ends_at<=now() and rating is null",
                        input.stars(),
                        id,
                        access.user(header).id()));
    }
}
