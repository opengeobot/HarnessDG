/*
 * 功能: 讨论应用服务。
 * 时间: 2026-07-04
 * 作者: AxeXie
 */
package com.aihub.asset.discussion.application;

import com.aihub.asset.discussion.domain.Comment;
import com.aihub.asset.discussion.domain.CommentStatus;
import com.aihub.asset.discussion.domain.DiscussionRepository;
import com.aihub.asset.discussion.domain.DiscussionSubscriptionRepository;
import com.aihub.asset.discussion.domain.DiscussionThread;
import com.aihub.asset.discussion.domain.ThreadStatus;
import com.aihub.audit.application.AuditEvent;
import com.aihub.audit.application.AuditService;
import com.aihub.audit.domain.AuditResult;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.identity.application.PrincipalQueryApplicationService;
import com.aihub.notification.application.NotificationService;
import com.aihub.notification.domain.NotificationSeverity;
import com.aihub.shared.api.CursorPage;
import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalType;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 讨论应用服务。
 *
 * <p>编排讨论线程与评论的创建、编辑、撤回、Moderator 操作，复用审计记录。
 * 权限继承资产授权：{@code asset:discuss} 参与讨论，{@code asset:moderate} Moderator 操作。
 */
@Service
public class DiscussionApplicationService {

    private static final Logger LOG = LoggerFactory.getLogger(DiscussionApplicationService.class);
    private static final Pattern MENTION_PATTERN =
            Pattern.compile("@((?:prn|usr|agt)_[A-Za-z0-9_-]+)");

    private final DiscussionRepository discussionRepository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final IdGenerator idGenerator;
    private final NotificationService notificationService;
    private final PrincipalQueryApplicationService principalQuery;
    private final DiscussionSubscriptionRepository subscriptionRepository;

    public DiscussionApplicationService(DiscussionRepository discussionRepository,
                                        AuthorizationService authorizationService,
                                        AuditService auditService,
                                        IdGenerator idGenerator,
                                        NotificationService notificationService,
                                        PrincipalQueryApplicationService principalQuery,
                                        DiscussionSubscriptionRepository subscriptionRepository) {
        this.discussionRepository = discussionRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.idGenerator = idGenerator;
        this.notificationService = notificationService;
        this.principalQuery = principalQuery;
        this.subscriptionRepository = subscriptionRepository;
    }

    /** 创建讨论线程。 */
    @Transactional
    public ThreadView createThread(String assetId, String title, String principalId) {
        authorizationService.requirePermission(Permissions.ASSET_DISCUSS);
        if (title == null || title.isBlank()) {
            throw new ValidationException("thread title is required");
        }
        if (title.length() > 256) {
            throw new ValidationException("thread title must not exceed 256 characters");
        }
        String threadId = idGenerator.generate(IdPrefix.THREAD);
        Instant now = Instant.now();
        DiscussionThread thread = new DiscussionThread(
                threadId, assetId, title.trim(), principalId,
                ThreadStatus.OPEN, 0, null, now, now);
        discussionRepository.insertThread(thread);
        auditDiscussion("DISCUSSION_CREATED", principalId, threadId, assetId,
                Map.of("title", title));
        publishOutbox("ASSET", assetId, "DISCUSSION_CREATED",
                Map.of("threadId", threadId, "assetId", assetId, "title", title));
        return ThreadView.from(thread);
    }

    /** 列出资产下的讨论线程（游标分页）。 */
    @Transactional(readOnly = true)
    public CursorPage<ThreadView> listThreads(String assetId, String cursor, int limit) {
        authorizationService.requirePermission(Permissions.ASSET_READ);
        int safeLimit = Math.max(1, Math.min(limit, 50));
        CursorPage<DiscussionThread> page = discussionRepository.listThreadsByAsset(
                assetId, cursor, safeLimit);
        return new CursorPage<>(
                page.items().stream().map(ThreadView::from).toList(),
                page.nextCursor(), page.hasMore());
    }

    /** 发表评论（顶层或回复）。 */
    @Transactional
    public CommentView createComment(String threadId, String parentId, String body,
                                     String principalId) {
        authorizationService.requirePermission(Permissions.ASSET_DISCUSS);
        DiscussionThread thread = loadThread(threadId);
        if (thread.status() != ThreadStatus.OPEN) {
            throw new ConflictException(ErrorCode.ASSET_STATE_NOT_ALLOWED,
                    "thread is not open for comments: " + thread.status(),
                    Map.of("threadId", threadId, "status", thread.status().name()));
        }
        validateBody(body);
        String commentId = idGenerator.generate(IdPrefix.COMMENT);
        Instant now = Instant.now();
        String trimmedBody = body.trim();
        Comment comment = new Comment(commentId, threadId, parentId, thread.assetId(),
                trimmedBody, principalId, CommentStatus.VISIBLE, 0, now, now);
        discussionRepository.insertComment(comment);
        thread.incrementCommentCount();
        discussionRepository.updateThread(thread);
        auditDiscussion("COMMENT_CREATED", principalId, commentId, thread.assetId(),
                Map.of("threadId", threadId));
        publishOutbox("ASSET", thread.assetId(), "COMMENT_CREATED",
                Map.of("commentId", commentId, "threadId", threadId,
                        "assetId", thread.assetId(), "createdBy", principalId));
        notifyMentionedPrincipals(trimmedBody, thread.assetId(), threadId, commentId, principalId);
        notifySubscribers(thread, commentId, principalId);
        return CommentView.from(comment);
    }

    /** 订阅资产讨论通知。 */
    @Transactional
    public void subscribe(String assetId, String principalId) {
        authorizationService.requirePermission(Permissions.ASSET_READ);
        subscriptionRepository.subscribe(assetId, principalId);
    }

    /** 取消订阅资产讨论通知。 */
    @Transactional
    public void unsubscribe(String assetId, String principalId) {
        authorizationService.requirePermission(Permissions.ASSET_READ);
        subscriptionRepository.unsubscribe(assetId, principalId);
    }

    /** 查询当前主体是否已订阅。 */
    @Transactional(readOnly = true)
    public boolean isSubscribed(String assetId, String principalId) {
        authorizationService.requirePermission(Permissions.ASSET_READ);
        return subscriptionRepository.isSubscribed(assetId, principalId);
    }

    /** 读取线程下的评论列表。 */
    @Transactional(readOnly = true)
    public List<CommentView> listComments(String threadId, String cursor, int limit) {
        authorizationService.requirePermission(Permissions.ASSET_READ);
        loadThread(threadId);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return discussionRepository.listCommentsByThread(threadId, cursor, safeLimit)
                .stream().map(CommentView::from).toList();
    }

    /** 编辑评论（作者操作，保留修订历史）。 */
    @Transactional
    public CommentView editComment(String commentId, String newBody, String principalId) {
        authorizationService.requirePermission(Permissions.ASSET_DISCUSS);
        validateBody(newBody);
        Comment comment = loadComment(commentId);
        if (!comment.createdBy().equals(principalId)) {
            throw new ConflictException(ErrorCode.ASSET_STATE_NOT_ALLOWED,
                    "only the comment author can edit", Map.of("commentId", commentId));
        }
        String oldBody = comment.editBody(newBody.trim());
        String revisionId = idGenerator.generate(IdPrefix.REVISION);
        discussionRepository.insertRevision(revisionId, commentId, oldBody, principalId);
        discussionRepository.updateComment(comment);
        return CommentView.from(comment);
    }

    /** 作者撤回评论。 */
    @Transactional
    public CommentView retractComment(String commentId, String principalId) {
        authorizationService.requirePermission(Permissions.ASSET_DISCUSS);
        Comment comment = loadComment(commentId);
        if (!comment.createdBy().equals(principalId)) {
            throw new ConflictException(ErrorCode.ASSET_STATE_NOT_ALLOWED,
                    "only the comment author can retract", Map.of("commentId", commentId));
        }
        try {
            comment.retract();
        } catch (IllegalStateException ex) {
            throw new ConflictException(ErrorCode.ASSET_STATE_NOT_ALLOWED, ex.getMessage(),
                    Map.of("commentId", commentId));
        }
        discussionRepository.updateComment(comment);
        auditDiscussion("COMMENT_RETRACTED", principalId, commentId, comment.assetId(),
                Map.of("threadId", comment.threadId()));
        return CommentView.from(comment);
    }

    /** Moderator 隐藏评论。 */
    @Transactional
    public CommentView hideComment(String commentId, String principalId) {
        authorizationService.requirePermission(Permissions.ASSET_MODERATE);
        Comment comment = loadComment(commentId);
        try {
            comment.hide();
        } catch (IllegalStateException ex) {
            throw new ConflictException(ErrorCode.ASSET_STATE_NOT_ALLOWED, ex.getMessage(),
                    Map.of("commentId", commentId));
        }
        discussionRepository.updateComment(comment);
        auditDiscussion("COMMENT_HIDDEN", principalId, commentId, comment.assetId(),
                Map.of("threadId", comment.threadId()));
        return CommentView.from(comment);
    }

    /** Moderator 恢复被隐藏的评论。 */
    @Transactional
    public CommentView unhideComment(String commentId, String principalId) {
        authorizationService.requirePermission(Permissions.ASSET_MODERATE);
        Comment comment = loadComment(commentId);
        try {
            comment.unhide();
        } catch (IllegalStateException ex) {
            throw new ConflictException(ErrorCode.ASSET_STATE_NOT_ALLOWED, ex.getMessage(),
                    Map.of("commentId", commentId));
        }
        discussionRepository.updateComment(comment);
        return CommentView.from(comment);
    }

    /** Moderator 锁定线程。 */
    @Transactional
    public ThreadView lockThread(String threadId, String principalId) {
        authorizationService.requirePermission(Permissions.ASSET_MODERATE);
        DiscussionThread thread = loadThread(threadId);
        try {
            thread.lock();
        } catch (IllegalStateException ex) {
            throw new ConflictException(ErrorCode.ASSET_STATE_NOT_ALLOWED, ex.getMessage(),
                    Map.of("threadId", threadId));
        }
        discussionRepository.updateThread(thread);
        auditDiscussion("THREAD_LOCKED", principalId, threadId, thread.assetId(), Map.of());
        return ThreadView.from(thread);
    }

    /** Moderator 解锁线程。 */
    @Transactional
    public ThreadView unlockThread(String threadId, String principalId) {
        authorizationService.requirePermission(Permissions.ASSET_MODERATE);
        DiscussionThread thread = loadThread(threadId);
        try {
            thread.unlock();
        } catch (IllegalStateException ex) {
            throw new ConflictException(ErrorCode.ASSET_STATE_NOT_ALLOWED, ex.getMessage(),
                    Map.of("threadId", threadId));
        }
        discussionRepository.updateThread(thread);
        return ThreadView.from(thread);
    }

    // ---- 私有辅助 ----

    private void notifyMentionedPrincipals(String body, String assetId, String threadId,
                                           String commentId, String authorId) {
        Set<String> mentioned = parseMentions(body);
        for (String mentionedId : mentioned) {
            if (mentionedId.equals(authorId)) {
                continue;
            }
            if (!principalQuery.existsByPrincipalId(mentionedId)) {
                continue;
            }
            PrincipalContext context = new PrincipalContext(
                    mentionedId, PrincipalType.USER, null, null, null, null, null, 0, null, null, null);
            if (!authorizationService.isResourcePermitted(
                    context, Permissions.ASSET_READ, "ASSET", assetId)) {
                continue;
            }
            Map<String, Object> params = Map.of(
                    "assetId", assetId, "threadId", threadId,
                    "commentId", commentId, "authorId", authorId);
            try {
                notificationService.sendInAppNotification(
                        mentionedId,
                        "DISCUSSION_MENTIONED",
                        "notification.discussion.mentioned",
                        NotificationSeverity.INFO,
                        params);
                publishOutbox("ASSET", assetId, "DISCUSSION_MENTIONED", params);
            } catch (Exception ex) {
                LOG.warn("failed to notify mention target={} assetId={}", mentionedId, assetId, ex);
            }
        }
    }

    private void notifySubscribers(DiscussionThread thread, String commentId, String authorId) {
        Set<String> subscribers = subscriptionRepository.findSubscribers(thread.assetId());
        Map<String, Object> params = Map.of(
                "assetId", thread.assetId(),
                "threadId", thread.threadId(),
                "commentId", commentId,
                "authorId", authorId);
        publishOutbox("ASSET", thread.assetId(), "DISCUSSION_REPLIED", params);
        notificationService.fanOutInAppNotifications(subscribers, authorId,
                "DISCUSSION_REPLIED",
                "notification.discussion.replied",
                NotificationSeverity.INFO,
                params);
    }

    static Set<String> parseMentions(String body) {
        if (body == null || body.isBlank()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = MENTION_PATTERN.matcher(body);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return Set.copyOf(result);
    }

    private DiscussionThread loadThread(String threadId) {
        return discussionRepository.findThread(threadId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ASSET_NOT_FOUND,
                        "discussion thread not found: " + threadId, Map.of()));
    }

    private Comment loadComment(String commentId) {
        return discussionRepository.findComment(commentId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ASSET_NOT_FOUND,
                        "comment not found: " + commentId, Map.of()));
    }

    private void validateBody(String body) {
        if (body == null || body.isBlank()) {
            throw new ValidationException("comment body is required");
        }
        if (body.length() > Comment.MAX_BODY_LENGTH) {
            throw new ValidationException(
                    "comment body must not exceed " + Comment.MAX_BODY_LENGTH + " characters");
        }
    }

    private void auditDiscussion(String eventType, String principalId, String resourceId,
                                 String assetId, Map<String, Object> attributes) {
        try {
            AuditEvent event = new AuditEvent(
                    eventType, eventType, principalId, null,
                    "ASSET", resourceId, null, null,
                    AuditResult.SUCCEEDED, null, attributes);
            auditService.record(event);
        } catch (Exception ex) {
            LOG.error("failed to record audit event eventType={} resourceId={}",
                    eventType, resourceId, ex);
        }
    }

    private void publishOutbox(String aggregateType, String aggregateId, String eventType,
                               Map<String, Object> payload) {
        try {
            notificationService.publishOutboxEvent(aggregateType, aggregateId, eventType,
                    payload, Map.of());
        } catch (Exception ex) {
            LOG.warn("failed to publish outbox event eventType={} aggregateId={}",
                    eventType, aggregateId, ex);
        }
    }
}
