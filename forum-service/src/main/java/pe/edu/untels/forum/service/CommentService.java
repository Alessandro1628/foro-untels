package pe.edu.untels.forum.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.untels.forum.client.AuthClient;
import pe.edu.untels.forum.client.BooksClient;
import pe.edu.untels.forum.dto.CommentResponse;
import pe.edu.untels.forum.dto.CreateCommentRequest;
import pe.edu.untels.forum.entity.Comment;
import pe.edu.untels.forum.repository.CommentRepository;

@Service
public class CommentService {
    private final CommentRepository comments;
    private final ForumAccess access;
    private final AuthClient auth;
    private final BooksClient books;

    public CommentService(CommentRepository comments, ForumAccess access, AuthClient auth, BooksClient books) {
        this.comments = comments;
        this.access = access;
        this.auth = auth;
        this.books = books;
    }

    @Transactional
    public Long createComment(Long postId, CreateCommentRequest request, String authorization) {
        Long actor = auth.getAuthenticatedUser(authorization).id();
        var book = request.bookId() == null ? null : books.resolve(request.bookId());
        ForumAccess.requireOpen(access.thread(postId, true));
        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setAuthorId(actor);
        comment.setContent(request.content());
        if (book != null) comment.attachBook(book.id(), book.title());
        return comments.save(comment).getId();
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> getCommentsByPost(Long postId) {
        access.thread(postId, false);
        return comments.findByPostIdAndStatusAndWithdrawnAtIsNullAndModeratedAtIsNullOrderByCreatedAtAscIdAsc(postId, "PUBLISHED")
                .stream().map(c -> new CommentResponse(c.getId(), c.getPostId(), c.getAuthorId(), c.getContent(),
                        c.getStatus(), c.getCreatedAt(), c.getBookId(), c.getBookTitle())).toList();
    }

    @Transactional
    public void edit(Long postId, Long id, String content, String authorization) {
        ownComment(postId, id, authorization).setContent(content);
    }

    @Transactional
    public void withdraw(Long postId, Long id, String authorization) {
        ownComment(postId, id, authorization).withdraw();
    }

    private Comment ownComment(Long postId, Long id, String authorization) {
        Long actor = auth.getAuthenticatedUser(authorization).id();
        access.thread(postId, true);
        Comment comment = access.comment(postId, id);
        ForumAccess.requireOwner(comment.getAuthorId(), actor);
        return comment;
    }
}
