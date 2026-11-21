package pe.edu.untels.forum.service;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.untels.forum.client.AuthClient;
import pe.edu.untels.forum.dto.AuthUserResponse;

@Service
public class ReportService {
    public record Pending(long id, long postId, Long commentId, String reason, String content) {}
    public enum Decision { DISMISSED, REMOVED }
    private record Target(long postId, Long commentId, String status) {}

    private final JdbcClient db;
    private final AuthClient auth;
    private final ForumAccess access;

    public ReportService(JdbcClient db, AuthClient auth, ForumAccess access) {
        this.db = db;
        this.auth = auth;
        this.access = access;
    }

    @Transactional
    public long report(Long postId, Long commentId, String reason, String authorization) {
        Long reporter = auth.getAuthenticatedUser(authorization).id();
        var post = access.thread(postId, true);
        if (commentId == null) ForumAccess.requireOpen(post);
        else access.comment(postId, commentId);
        return db.sql("""
                insert into public.reports(post_id, comment_id, reporter_id, reason)
                values (:post, :comment, :reporter, :reason) returning id
                """).param("post", postId).param("comment", commentId, java.sql.Types.BIGINT)
                .param("reporter", reporter).param("reason", reason.strip()).query(Long.class).single();
    }

    @Transactional(readOnly = true)
    public List<Pending> pending(String authorization) {
        moderator(authorization);
        return db.sql("""
                select r.id, r.post_id, r.comment_id, r.reason,
                       case when r.comment_id is null then p.title || ' — ' || p.content else c.content end as content
                from public.reports r join public.posts p on p.id = r.post_id
                left join public.comments c on c.id = r.comment_id
                where r.status = 'PENDING' order by r.created_at, r.id
                """).query((rs, i) -> new Pending(rs.getLong("id"), rs.getLong("post_id"),
                        rs.getObject("comment_id", Long.class), rs.getString("reason"), rs.getString("content"))).list();
    }

    @Transactional
    public void resolve(long id, Decision decision, String reason, String authorization) {
        Long actor = moderator(authorization).id();
        Target target = db.sql("select post_id, comment_id, status from public.reports where id = ? for update")
                .param(id).query((rs, i) -> new Target(rs.getLong(1), rs.getObject(2, Long.class), rs.getString(3)))
                .optional().orElseThrow(ForumAccess::missing);
        if (!"PENDING".equals(target.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El reporte ya fue resuelto");
        }
        if (decision == Decision.REMOVED) {
            // A previous report can already have hidden this target. Record both decisions.
            if (target.commentId() == null) {
                db.sql("update public.posts set moderated_at = coalesce(moderated_at, current_timestamp) where id = ?")
                        .param(target.postId()).update();
            } else {
                db.sql("update public.comments set moderated_at = coalesce(moderated_at, current_timestamp) where post_id = ? and id = ?")
                        .param(target.postId()).param(target.commentId()).update();
            }
        }
        db.sql("""
                update public.reports set status = ?, resolved_by = ?, decision_reason = ?, resolved_at = current_timestamp
                where id = ?
                """).param(decision.name()).param(actor).param(reason.strip()).param(id).update();
    }

    private AuthUserResponse moderator(String authorization) {
        var actor = auth.getAuthenticatedUser(authorization);
        if (!"MODERATOR".equals(actor.role()) && !"ADMIN".equals(actor.role()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requieren permisos de moderación");
        return actor;
    }
}
