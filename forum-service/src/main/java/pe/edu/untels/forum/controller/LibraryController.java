package pe.edu.untels.forum.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import pe.edu.untels.forum.service.*;

import java.io.IOException;
import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/api/v1/academic/materials")
public class LibraryController {
    private final JdbcTemplate db;
    private final AcademicAccess access;
    private static final String DETAILS =
            """
select m.id,m.title,m.author,m.shared_by,m.owner_id,m.subject_id,s.name as subject,m.kind,m.url,m.status,
m.version,%s exists(select from credentials a where a.account=m.owner_id
and a.kind='TEACHER' and a.status='APPROVED') as teacher from materials m join subjects s on s.id=m.subject_id
""";

    public LibraryController(JdbcTemplate db, AcademicAccess access) {
        this.db = db;
        this.access = access;
    }

    @GetMapping
    public List<Map<String, Object>> catalog(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) Long subject) {
        String query = DETAILS.formatted("") + " where m.status='APPROVED' and strpos(lower(m.title),lower(?))>0";
        return subject == null
                ? db.queryForList(query + " order by m.title", q)
                : db.queryForList(query + " and m.subject_id=? order by m.title", q, subject);
    }

    @GetMapping("/mine")
    public List<Map<String, Object>> mine(
            @RequestHeader(value = "Authorization", required = false) String header) {
        return db.queryForList(
                DETAILS.formatted("m.observation,") + " where m.owner_id=? order by m.id desc", access.user(header).id());
    }

    @GetMapping("/pending")
    public List<Map<String, Object>> pending(
            @RequestHeader(value = "Authorization", required = false) String header) {
        access.reviewer(header, false);
        return db.queryForList(DETAILS.formatted("m.observation,") + " where m.status='PENDING' order by m.id");
    }

    @GetMapping("/{id}/history")
    public List<Map<String, Object>> history(
            @PathVariable long id,
            @RequestHeader(value = "Authorization", required = false) String header) {
        var user = access.user(header);
        AcademicAccess.requiredId(
                db.queryForList(
                        "select id from materials where id=? and (owner_id=? or ?)",
                        Long.class,
                        id,
                        user.id(),
                        "ADMIN".equals(user.role())));
        return db.queryForList(
                "select version,decision,reason from material_reviews where material_id=? order by"
                    + " version",
                id);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> document(
            @PathVariable long id,
            @RequestHeader(value = "Authorization", required = false) String header) {
        var user = header == null ? null : access.user(header);
        var rows =
                db.queryForList(
                        """
select document from materials where id=? and kind='PDF' and (status='APPROVED' or owner_id=? or ?)
""",
                        byte[].class,
                        id,
                        user == null ? null : user.id(),
                        user != null && "ADMIN".equals(user.role()));
        if (rows.isEmpty()) throw ForumAccess.missing();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=\"material.pdf\"")
                .header("Content-Security-Policy", "sandbox")
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(rows.getFirst());
    }

    public record Submission(
            @NotBlank @Size(max = 180) String title,
            @NotBlank @Size(max = 160) String author,
            @NotNull Long subject,
            @Size(max = 2000) String url) {}

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public long submit(
            @Valid @ModelAttribute Submission data,
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) Long revision,
            @RequestHeader(value = "Authorization", required = false) String header)
            throws IOException {
        var user = access.user(header);
        access.activeSubject(data.subject());
        String url = data.url() == null || data.url().isBlank() ? null : data.url().strip();
        boolean pdf = file != null && !file.isEmpty();
        if (pdf == (url != null)) throw bad();
        byte[] bytes = null;
        if (pdf) {
            if (file.getSize() > 4 * 1024 * 1024) throw bad();
            bytes = file.getBytes();
            if (bytes.length < 5
                    || !new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)
                            .equals("%PDF-")) throw bad();
        } else {
            try {
                URI uri = URI.create(url);
                if (!Set.of("https", "http")
                                .contains(
                                        uri.getScheme() == null
                                                ? ""
                                                : uri.getScheme().toLowerCase(Locale.ROOT))
                        || uri.getHost() == null
                        || uri.getUserInfo() != null) throw bad();
            } catch (IllegalArgumentException e) {
                throw bad();
            }
        }
        String kind = pdf ? "PDF" : "LINK";
        if (revision != null) {
            AcademicAccess.changed(
                    db.update(
                            """
update materials set title=?,author=?,subject_id=?,kind=?,url=?,document=?,status='PENDING',version=version+1
where id=? and owner_id=? and status='OBSERVED'
""",
                            data.title().strip(),
                            data.author().strip(),
                            data.subject(),
                            kind,
                            url,
                            bytes,
                            revision,
                            user.id()));
            return revision;
        }
        return db.queryForObject(
                """
                insert into materials(owner_id,shared_by,title,author,subject_id,kind,url,document)
                values (?,?,?,?,?,?,?,?) returning id
                """,
                Long.class,
                user.id(),
                user.fullName(),
                data.title().strip(),
                data.author().strip(),
                data.subject(),
                kind,
                url,
                bytes);
    }

    public record Review(
            @NotNull Boolean approve, @Size(max = 500) String reason, @Min(1) int version) {}

    @PutMapping("/{id}/review")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void review(
            @PathVariable long id,
            @RequestBody @Valid Review data,
            @RequestHeader(value = "Authorization", required = false) String header) {
        long reviewer = access.reviewer(header, false).id();
        if (!data.approve() && (data.reason() == null || data.reason().isBlank())) throw bad();
        String decision = data.approve() ? "APPROVED" : "OBSERVED";
        AcademicAccess.changed(
                db.update(
                        """
                        update materials set status=?,reviewer=?,observation=coalesce(?,observation)
                        where id=? and version=? and status='PENDING'
                        """,
                        decision,
                        reviewer,
                        data.reason(),
                        id,
                        data.version()));
        db.update(
                "insert into material_reviews(material_id,version,reviewer,decision,reason) values"
                    + " (?,?,?,?,?)",
                id,
                data.version(),
                reviewer,
                decision,
                data.reason());
    }

    private static ResponseStatusException bad() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ficha o archivo inválido");
    }
}
