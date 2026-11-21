package pe.edu.untels.forum.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import pe.edu.untels.forum.service.AcademicAccess;

import java.util.*;

@RestController
@RequestMapping("/api/v1/academic")
public class DirectoryController {
    private final JdbcTemplate db;
    private final AcademicAccess access;

    public DirectoryController(JdbcTemplate db, AcademicAccess access) {
        this.db = db;
        this.access = access;
    }

    public record Name(@NotBlank @Size(max = 80) String name) {}

    public record Community(
            @NotBlank @Size(max = 100) String name, @NotBlank @Size(max = 500) String purpose) {}

    public record Decision(@NotNull Boolean approve) {}

    private String catalog(String kind) {
        return switch (kind) {
            case "subjects" -> "subjects";
            case "categories" -> "categories";
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        };
    }

    @GetMapping("/catalogs/{kind}")
    public List<Map<String, Object>> catalogList(@PathVariable String kind) {
        return db.queryForList("select id,name,active from " + catalog(kind) + " order by name");
    }

    @PostMapping("/catalogs/{kind}")
    @ResponseStatus(HttpStatus.CREATED)
    public long add(
            @PathVariable String kind,
            @RequestBody @Valid Name data,
            @RequestHeader(value = "Authorization", required = false) String header) {
        access.reviewer(header, false);
        return db.queryForObject(
                "insert into " + catalog(kind) + "(name) values (?) returning id",
                Long.class,
                data.name().strip());
    }

    @PutMapping("/catalogs/{kind}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void rename(
            @PathVariable String kind,
            @PathVariable long id,
            @RequestBody @Valid Name data,
            @RequestHeader(value = "Authorization", required = false) String header) {
        access.reviewer(header, false);
        String name = data.name().strip();
        AcademicAccess.changed(
                db.update(
                        "update " + catalog(kind) + " set name=? where id=? and active", name, id));
        if ("categories".equals(kind))
            db.update("update posts set category=? where category_id=?", name, id);
    }

    @DeleteMapping("/catalogs/{kind}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(
            @PathVariable String kind,
            @PathVariable long id,
            @RequestHeader(value = "Authorization", required = false) String header) {
        access.reviewer(header, false);
        AcademicAccess.changed(
                db.update(
                        "update " + catalog(kind) + " set active=false where id=? and active", id));
    }

    @GetMapping("/communities")
    public List<Map<String, Object>> directory(
            @RequestHeader(value = "Authorization", required = false) String header) {
        Long actor = header == null ? null : access.user(header).id();
        return db.queryForList(
                """
                select c.id,c.name,c.purpose,(f.user_id is not null) as following from communities c
                left join follows f on f.community_id=c.id and f.user_id=? order by c.name
                """,
                actor);
    }

    @PostMapping("/communities")
    @ResponseStatus(HttpStatus.CREATED)
    public long create(
            @Valid @RequestBody Community data,
            @RequestHeader(value = "Authorization", required = false) String header) {
        access.reviewer(header, false);
        return db.queryForObject(
                "insert into communities(name,purpose) values (?,?) returning id",
                Long.class,
                data.name().strip(),
                data.purpose().strip());
    }

    @PostMapping("/proposals")
    @ResponseStatus(HttpStatus.CREATED)
    public long propose(
            @RequestBody @Valid Community data,
            @RequestHeader(value = "Authorization", required = false) String header) {
        var actor = access.user(header);
        return AcademicAccess.requiredId(
                db.queryForList(
                        """
insert into community_proposals(name,purpose,proposer) select ?,?,?
where not exists(select from communities where lower(name)=lower(?)) returning id
""",
                        Long.class,
                        data.name().strip(),
                        data.purpose().strip(),
                        actor.id(),
                        data.name().strip()));
    }

    @GetMapping("/proposals")
    public List<Map<String, Object>> proposals(
            @RequestHeader(value = "Authorization", required = false) String header) {
        access.reviewer(header, true);
        return db.queryForList(
                "select id,name,purpose from community_proposals where status='PENDING' order by"
                    + " id");
    }

    @PutMapping("/proposals/{id}")
    @Transactional
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolve(
            @PathVariable long id,
            @Valid @RequestBody Decision data,
            @RequestHeader(value = "Authorization", required = false) String header) {
        var actor = access.reviewer(header, true);
        AcademicAccess.changed(
                db.update(
                        "update community_proposals set status=?,reviewer=? where id=? and"
                            + " status='PENDING'",
                        data.approve() ? "APPROVED" : "REJECTED",
                        actor.id(),
                        id));
        if (data.approve())
            db.update(
                    "insert into communities(name,purpose) select name,purpose from"
                        + " community_proposals where id=?",
                    id);
    }

    @PutMapping("/communities/{id}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void follow(
            @PathVariable long id,
            @RequestHeader(value = "Authorization", required = false) String header) {
        var actor = access.user(header);
        if (db.queryForList("select id from communities where id=?", Long.class, id).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        db.update(
                "insert into follows(user_id,community_id) values (?,?) on conflict do nothing",
                actor.id(),
                id);
    }

    @DeleteMapping("/communities/{id}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollow(
            @PathVariable long id,
            @RequestHeader(value = "Authorization", required = false) String header) {
        db.update(
                "delete from follows where community_id=? and user_id=?",
                id,
                access.user(header).id());
    }
}
