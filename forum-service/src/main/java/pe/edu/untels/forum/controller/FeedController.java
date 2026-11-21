package pe.edu.untels.forum.controller;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import pe.edu.untels.forum.service.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/academic/feed")
public class FeedController {
    private final JdbcTemplate db;
    private final AcademicAccess access;
    private final ForumAccess forum;

    public FeedController(JdbcTemplate db, AcademicAccess access, ForumAccess forum) {
        this.db = db;
        this.access = access;
        this.forum = forum;
    }

    public enum Scope {
        ALL,
        FOLLOWING,
        FAVORITES
    }

    public enum Sort {
        RECENT,
        FEATURED
    }

    @GetMapping
    public List<Map<String, Object>> feed(
            @RequestParam(defaultValue = "ALL") Scope scope,
            @RequestParam(defaultValue = "RECENT") Sort sort,
            @RequestParam(required = false) Long community,
            @RequestHeader(value = "Authorization", required = false) String header) {
        Long user = header != null || scope != Scope.ALL ? access.user(header).id() : null;
        String query =
                """
select p.id, case when p.withdrawn_at is null then p.author_id end as "authorId",
case when p.withdrawn_at is null then p.title else 'Consulta retirada' end as title,
case when p.withdrawn_at is null then p.content else '' end as content,
case when p.withdrawn_at is null then p.category end as category,
case when p.withdrawn_at is null then c.name end as "communityName",
case when p.withdrawn_at is null then 'PUBLISHED' else 'WITHDRAWN' end as status,
p.created_at as "createdAt", (f.user_id is not null) as favorite,
(v.user_id is not null) as voted, (select count(*) from useful_votes uv where uv.post_id=p.id) as votes
from posts p left join communities c on c.id=p.community_id
left join favorites f on f.post_id=p.id and f.user_id=?
left join useful_votes v on v.post_id=p.id and v.user_id=?
left join follows s on s.community_id=c.id and s.user_id=?
where p.status='PUBLISHED' and p.moderated_at is null
and (p.withdrawn_at is null or exists(select from comments cm where cm.post_id=p.id and cm.status='PUBLISHED'
and cm.withdrawn_at is null and cm.moderated_at is null))
""";
        var args = new ArrayList<Object>(Arrays.asList(user, user, user));
        if (community != null) {
            query += " and c.id=?";
            args.add(community);
        }
        if (scope == Scope.FOLLOWING) query += " and s.user_id is not null";
        if (scope == Scope.FAVORITES)
            query += " and f.user_id is not null and p.withdrawn_at is null";
        query +=
                sort == Sort.FEATURED
                        ? " order by votes desc, p.created_at desc,p.id desc"
                        : " order by p.created_at desc,p.id desc";
        return db.queryForList(query, args.toArray());
    }

    public enum Mark {
        favorite,
        vote
    }

    @PutMapping("/{id}/{mark}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void mark(
            @PathVariable long id,
            @PathVariable Mark mark,
            @RequestHeader(value = "Authorization", required = false) String header) {
        long user = access.user(header).id();
        ForumAccess.requireOpen(forum.thread(id, true));
        String table = mark == Mark.vote ? "useful_votes" : "favorites";
        db.update(
                "insert into " + table + "(post_id,user_id) values (?,?) on conflict do nothing",
                id,
                user);
    }

    @DeleteMapping("/{id}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            @PathVariable long id,
            @RequestHeader(value = "Authorization", required = false) String header) {
        db.update(
                "delete from favorites where post_id=? and user_id=?",
                id,
                access.user(header).id());
    }
}
