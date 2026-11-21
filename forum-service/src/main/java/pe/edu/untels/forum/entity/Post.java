
package pe.edu.untels.forum.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "posts", schema = "public")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "title", nullable = false, length = 180)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "category", nullable = false, length = 80)
    private String category = "General";

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id")
    private Community community;

    public Community getCommunity() { return community; }
    public void setCommunity(Community community) { this.community = community; }

    public Post() {
    }

    @PreUpdate
    public void updateTimestamp() {
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    @Column(name="category_id")
    private Long categoryId;
    public void setCategoryId(Long id) { categoryId=id; }

    public Long getId() {
        return id;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
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

}
