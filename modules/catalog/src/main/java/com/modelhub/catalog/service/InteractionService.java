package com.modelhub.catalog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modelhub.catalog.access.RepositoryAccessFacade;
import com.modelhub.catalog.access.RepositoryAccessFacade.RepoContext;
import com.modelhub.catalog.access.RepositoryAccessFacade.RepoRole;
import com.modelhub.catalog.access.RepositoryAccessFacade.VisibleScope;
import com.modelhub.catalog.domain.FeedbackEntity;
import com.modelhub.catalog.domain.RepositoryEntity;
import com.modelhub.catalog.repo.FeedbackRepository;
import com.modelhub.catalog.repo.RepositoryFavoriteRepository;
import com.modelhub.catalog.repo.RepositoryLikeRepository;
import com.modelhub.identity.security.CurrentPrincipal;
import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.id.PublicIds;
import com.modelhub.shared.paging.CursorQuery;
import com.modelhub.shared.paging.CursorResult;
import com.modelhub.shared.paging.PageQuery;
import com.modelhub.shared.paging.PageResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 互动服务（03 §6.1/04 §5）：点赞/收藏 Toggle 禁止——POST/DELETE 幂等语义，
 * UNIQUE 约束兜底并发（事务中不得先查后改而不处理冲突）；写入事实行与 Outbox 事件
 * 同一事务，repository_stats 由幂等消费者（StatsEventHandler）重算。
 */
@Service
public class InteractionService {

    private static final Logger log = LoggerFactory.getLogger(InteractionService.class);
    private static final int FEEDBACK_MAX_LEN = 10000;
    private static final Set<String> ME_TABS = Set.of("created", "likes", "favorites");

    /** 契约 RelationshipState：likes/favorite 响应体。 */
    public record RelationshipState(boolean active) {}

    /** 契约 Feedback schema（04 §5）：id/author/content/createdAt。 */
    public record FeedbackView(UUID id, String author, String content, OffsetDateTime createdAt) {}

    private final RepositoryLikeRepository likes;
    private final RepositoryFavoriteRepository favorites;
    private final FeedbackRepository feedbacks;
    private final RepositoryAccessFacade access;
    private final CatalogService catalog;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager em;

    public InteractionService(RepositoryLikeRepository likes, RepositoryFavoriteRepository favorites,
                              FeedbackRepository feedbacks, RepositoryAccessFacade access,
                              CatalogService catalog, OutboxService outbox, ObjectMapper objectMapper) {
        this.likes = likes;
        this.favorites = favorites;
        this.feedbacks = feedbacks;
        this.access = access;
        this.catalog = catalog;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    /** 幂等设为已点赞：已存在直接返回，重复调用不切换（04 §5 禁止 Toggle）；
     *  INSERT ... ON CONFLICT DO NOTHING 兜底并发，绝不抛竞态异常（03 §6.1）。 */
    @Transactional
    public RelationshipState like(CurrentPrincipal actor, UUID repoId) {
        RepoContext ctx = access.authorize(repoId, actor, RepoRole.READ);
        Long userId = actor.userId();
        if (likes.existsByUserIdAndRepositoryId(userId, ctx.repo().getId())) {
            return new RelationshipState(true);
        }
        likes.insertIgnore(userId, ctx.repo().getId());
        outbox.publish("RepositoryLiked", repoId.toString(), null,
                Map.of("repositoryId", ctx.repo().getId()));
        return new RelationshipState(true);
    }

    /** 幂等取消点赞：不存在也视为成功（04 §5）。 */
    @Transactional
    public void unlike(CurrentPrincipal actor, UUID repoId) {
        RepoContext ctx = access.authorize(repoId, actor, RepoRole.READ);
        likes.deleteByUserIdAndRepositoryId(actor.userId(), ctx.repo().getId());
        outbox.publish("RepositoryUnliked", repoId.toString(), null,
                Map.of("repositoryId", ctx.repo().getId()));
    }

    /** 幂等设为已收藏（并发兜底同 like）。 */
    @Transactional
    public RelationshipState favorite(CurrentPrincipal actor, UUID repoId) {
        RepoContext ctx = access.authorize(repoId, actor, RepoRole.READ);
        Long userId = actor.userId();
        if (favorites.existsByUserIdAndRepositoryId(userId, ctx.repo().getId())) {
            return new RelationshipState(true);
        }
        favorites.insertIgnore(userId, ctx.repo().getId());
        outbox.publish("RepositoryFavorited", repoId.toString(), null,
                Map.of("repositoryId", ctx.repo().getId()));
        return new RelationshipState(true);
    }

    /** 幂等取消收藏。 */
    @Transactional
    public void unfavorite(CurrentPrincipal actor, UUID repoId) {
        RepoContext ctx = access.authorize(repoId, actor, RepoRole.READ);
        favorites.deleteByUserIdAndRepositoryId(actor.userId(), ctx.repo().getId());
        outbox.publish("RepositoryUnfavorited", repoId.toString(), null,
                Map.of("repositoryId", ctx.repo().getId()));
    }

    /** 创建反馈（04 §5）：授权 READ 后写入；v1 moderation_status 默认 approved。 */
    @Transactional
    public FeedbackView createFeedback(CurrentPrincipal actor, UUID repoId, String content) {
        RepoContext ctx = access.authorize(repoId, actor, RepoRole.READ);
        if (content == null || content.isBlank() || content.length() > FEEDBACK_MAX_LEN) {
            throw ApiException.badRequest("反馈内容长度须在 1..10000 之间",
                    List.of(new ApiException.Detail("content", "out_of_range")));
        }
        FeedbackEntity e = new FeedbackEntity();
        e.setPublicId(PublicIds.next());
        e.setRepositoryId(ctx.repo().getId());
        e.setUserId(actor.userId());
        e.setContent(content);
        try {
            e.setAuthorSnapshot(objectMapper.writeValueAsString(
                    Map.of("username", actor.username(), "userId", actor.userPublicId().toString())));
        } catch (Exception ex) {
            throw new IllegalStateException("author snapshot 序列化失败", ex);
        }
        e.setModerationStatus("approved");
        e.setCreatedAt(OffsetDateTime.now());
        feedbacks.saveAndFlush(e);
        return new FeedbackView(e.getPublicId(), actor.username(), e.getContent(), e.getCreatedAt());
    }

    /** 反馈列表（04 §5）：cursor 分页新→旧（created_at DESC, id DESC），匿名可读
     *  （authorize READ 决定 403/404 防枚举）。 */
    @Transactional(readOnly = true)
    public CursorResult<FeedbackView> listFeedbacks(CurrentPrincipal actor, UUID repoId, CursorQuery cursor) {
        RepoContext ctx = access.authorize(repoId, actor, RepoRole.READ);
        // CursorQuery 无 cursor 时 lastKey = Long.MIN_VALUE，id 降序游标首屏从 MAX_VALUE 起
        long lastId = cursor.lastKey() == Long.MIN_VALUE ? Long.MAX_VALUE : cursor.lastKey();
        List<FeedbackEntity> all = feedbacks.findByRepositoryIdAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
                ctx.repo().getId(), lastId,
                org.springframework.data.domain.PageRequest.of(0, Math.max(cursor.limit() + 1, 1)));
        boolean hasMore = all.size() > cursor.limit();
        List<FeedbackEntity> pageItems = all.stream().limit(cursor.limit()).toList();
        List<FeedbackView> items = new ArrayList<>();
        for (FeedbackEntity f : pageItems) {
            items.add(new FeedbackView(f.getPublicId(), authorName(f), f.getContent(), f.getCreatedAt()));
        }
        String next = hasMore && !items.isEmpty()
                ? CursorQuery.encode(pageItems.get(pageItems.size() - 1).getId()) : null;
        return new CursorResult<>(items, next);
    }

    /** 个人中心仓库列表（04 §5 /me/repositories）：tab ∈ {created, likes, favorites}，
     *  可选 type 按 resource_type 过滤（契约 typeKey pattern 由 controller 校验），
     *  页码分页；结果全部经 computeVisibleScope 过滤（ME-001 不泄漏私有资源），
     *  total 以过滤后计。 */
    @Transactional(readOnly = true)
    public PageResult<CatalogService.RepoView> listMine(CurrentPrincipal actor, String tab, String type,
                                                        PageQuery page) {
        if (tab == null || !ME_TABS.contains(tab)) {
            throw ApiException.badRequest("未知 tab 值: " + tab,
                    List.of(new ApiException.Detail("tab", "unknown_value")));
        }
        VisibleScope scope = access.computeVisibleScope(actor);
        StringBuilder where = new StringBuilder(" WHERE r.lifecycle_status IN ('active','archived') ");
        Map<String, Object> qp = new HashMap<>();
        switch (tab) {
            case "created" -> where.append("AND r.created_by_user_id = :me ");
            case "likes" -> where.append("AND EXISTS (SELECT 1 FROM repository_likes lk "
                    + "WHERE lk.repository_id = r.id AND lk.user_id = :me) ");
            case "favorites" -> where.append("AND EXISTS (SELECT 1 FROM repository_favorites fv "
                    + "WHERE fv.repository_id = r.id AND fv.user_id = :me) ");
            default -> throw new IllegalStateException("unreachable tab: " + tab);
        }
        if (type != null && !type.isBlank()) {
            where.append("AND r.resource_type = :mType ");
            qp.put("mType", type);
        }
        qp.put("me", actor.userId());
        // includeSelfOwned=true：/me/repositories 以当前用户为主体，其自创私有仓库
        // 不在 visibleNamespaceIds/collaboratorRepoIds 中（namespace 成员关系只包含
        // 组织 namespace，个人 namespace 的私有仓库需按 created_by_user_id 显式可见），
        // 否则用户自赞/收藏的私有仓库会被可见范围过滤掉（ME-001 反向缺陷）。
        catalog.appendScope(where, scope, qp, true);

        long total = countMine(where.toString(), qp);
        String sql = "SELECT r.* FROM repositories r "
                + "LEFT JOIN repository_stats s ON s.repository_id = r.id "
                + where + " ORDER BY r.updated_at DESC, r.public_id DESC LIMIT :limit OFFSET :offset";
        Query query = em.createNativeQuery(sql, RepositoryEntity.class);
        qp.forEach(query::setParameter);
        query.setParameter("limit", page.pageSize());
        query.setParameter("offset", page.offset());
        @SuppressWarnings("unchecked")
        List<RepositoryEntity> repos = query.getResultList();
        return PageResult.of(total, page, catalog.toViews(repos));
    }

    private long countMine(String where, Map<String, Object> qp) {
        Query query = em.createNativeQuery("SELECT COUNT(*) FROM repositories r " + where);
        qp.forEach(query::setParameter);
        return ((Number) query.getSingleResult()).longValue();
    }

    /** 作者展示名：优先快照，缺失时以用户 id 兜底（快照不随改名丢失，03 §6.1）。 */
    private String authorName(FeedbackEntity f) {
        if (f.getAuthorSnapshot() == null) {
            return "user:" + f.getUserId();
        }
        try {
            var node = objectMapper.readTree(f.getAuthorSnapshot());
            String name = node.path("username").asText("");
            return name.isBlank() ? "user:" + f.getUserId() : name;
        } catch (Exception ex) {
            log.warn("feedback author snapshot 解析失败 id={}", f.getId());
            return "user:" + f.getUserId();
        }
    }
}
