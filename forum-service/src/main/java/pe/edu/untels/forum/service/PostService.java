package pe.edu.untels.forum.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.untels.forum.entity.Community;
import pe.edu.untels.forum.repository.CommunityRepository;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.untels.forum.client.AuthClient;
import pe.edu.untels.forum.dto.CreatePostRequest;
import pe.edu.untels.forum.dto.PostResponse;
import pe.edu.untels.forum.entity.Post;
import pe.edu.untels.forum.repository.PostRepository;

@Service
public class PostService {
    private final PostRepository posts;
    private final AuthClient auth;
    private final ForumAccess access;

    private final CommunityRepository communities;

    public PostService(PostRepository posts, AuthClient auth, ForumAccess access, CommunityRepository communities) {
        this.communities = communities;
        this.posts = posts;
        this.auth = auth;
        this.access = access;
    }

    @Transactional
    public Long createPost(CreatePostRequest request, String authorization) {
        Post post = new Post();
        post.setAuthorId(auth.getAuthenticatedUser(authorization).id());
        if (request.communityId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona una comunidad");
        }
        Community community = communities.findById(request.communityId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "La comunidad seleccionada no existe"));
        post.setCommunity(community);
        apply(post, request);
        return posts.save(post).getId();
    }

    @Transactional
    public void edit(Long id, CreatePostRequest request, String authorization) {
        Post post = ownThread(id, authorization);
        ForumAccess.requireOpen(post);
        apply(post, request);
    }

    @Transactional
    public void withdraw(Long id, String authorization) {
        Post post = ownThread(id, authorization);
        if (!post.isWithdrawn()) post.withdraw();
    }

    @Transactional(readOnly = true)
    public List<Community> listCommunities() {
        return communities.findAllByOrderByNameAsc();
    }

    private Post ownThread(Long id, String authorization) {
        Long actor = auth.getAuthenticatedUser(authorization).id();
        Post post = access.thread(id, true);
        ForumAccess.requireOwner(post.getAuthorId(), actor);
        return post;
    }

    private void apply(Post post, CreatePostRequest request) {
        post.setTitle(request.title());
        post.setContent(request.content());
        post.setCategory(request.category());
    }

    @Transactional(readOnly = true)
    public List<PostResponse> getPublishedPosts() {
        return posts.visibleFeed().stream().map(post -> post.isWithdrawn()
                ? new PostResponse(post.getId(), null, "Consulta retirada", "", null, null, "WITHDRAWN", post.getCreatedAt())
                : new PostResponse(post.getId(), post.getAuthorId(), post.getTitle(), post.getContent(),
                        post.getCategory(), post.getCommunity() == null ? null : post.getCommunity().getName(),
                        post.getStatus(), post.getCreatedAt())).toList();
    }
}
