
package pe.edu.untels.forum.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "comments", schema = "public")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PUBLISHED";

    @Column(
            name = "created_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private OffsetDateTime createdAt;

    @Column(
            name = "updated_at",
            nullable = false,
            insertable = false
    )
    private OffsetDateTime updatedAt;

    public Comment() {
    }

    @PreUpdate
    public void updateTimestamp() {
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Column(name = "withdrawn_at")
    private OffsetDateTime withdrawnAt;
    @Column(name = "moderated_at")
    private OffsetDateTime moderatedAt;

    public boolean isWithdrawn() { return withdrawnAt != null; }
    public boolean isModerated() { return moderatedAt != null; }
    public void withdraw() { withdrawnAt = OffsetDateTime.now(ZoneOffset.UTC); }
    public void moderate() { moderatedAt = OffsetDateTime.now(ZoneOffset.UTC); }


    @Column(name = "book_id", length = 64)
    private String bookId;
    @Column(name = "book_title", columnDefinition = "text")
    private String bookTitle;

    public String getBookId() { return bookId; }
    public String getBookTitle() { return bookTitle; }
    public void attachBook(String id, String title) { bookId = id; bookTitle = title; }

}
