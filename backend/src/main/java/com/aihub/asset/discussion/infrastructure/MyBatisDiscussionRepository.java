package com.aihub.asset.discussion.infrastructure;

import com.aihub.asset.discussion.domain.Comment;
import com.aihub.asset.discussion.domain.CommentStatus;
import com.aihub.asset.discussion.domain.DiscussionRepository;
import com.aihub.asset.discussion.domain.DiscussionThread;
import com.aihub.asset.discussion.domain.ThreadStatus;
import com.aihub.shared.api.CursorPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 讨论仓储适配器。
 */
@Repository
public class MyBatisDiscussionRepository implements DiscussionRepository {

    private final DiscussionThreadMapper threadMapper;
    private final CommentMapper commentMapper;
    private final CommentRevisionMapper revisionMapper;

    public MyBatisDiscussionRepository(DiscussionThreadMapper threadMapper,
                                       CommentMapper commentMapper,
                                       CommentRevisionMapper revisionMapper) {
        this.threadMapper = threadMapper;
        this.commentMapper = commentMapper;
        this.revisionMapper = revisionMapper;
    }

    @Override
    public void insertThread(DiscussionThread thread) {
        DiscussionThreadEntity entity = new DiscussionThreadEntity();
        entity.setThreadId(thread.threadId());
        entity.setAssetId(thread.assetId());
        entity.setTitle(thread.title());
        entity.setCreatedBy(thread.createdBy());
        entity.setStatus(thread.status().name());
        entity.setCommentCount(thread.commentCount());
        entity.setLastCommentAt(thread.lastCommentAt());
        entity.setCreatedAt(thread.createdAt());
        entity.setUpdatedAt(thread.updatedAt());
        threadMapper.insert(entity);
    }

    @Override
    public Optional<DiscussionThread> findThread(String threadId) {
        DiscussionThreadEntity entity = threadMapper.selectOne(
                Wrappers.<DiscussionThreadEntity>lambdaQuery()
                        .eq(DiscussionThreadEntity::getThreadId, threadId));
        return entity == null ? Optional.empty() : Optional.of(toThread(entity));
    }

    @Override
    public void updateThread(DiscussionThread thread) {
        threadMapper.update(null, Wrappers.<DiscussionThreadEntity>lambdaUpdate()
                .eq(DiscussionThreadEntity::getThreadId, thread.threadId())
                .set(DiscussionThreadEntity::getStatus, thread.status().name())
                .set(DiscussionThreadEntity::getCommentCount, thread.commentCount())
                .set(DiscussionThreadEntity::getLastCommentAt, thread.lastCommentAt())
                .set(DiscussionThreadEntity::getUpdatedAt, thread.updatedAt()));
    }

    @Override
    public CursorPage<DiscussionThread> listThreadsByAsset(String assetId, String cursor, int limit) {
        var wrapper = Wrappers.<DiscussionThreadEntity>lambdaQuery()
                .eq(DiscussionThreadEntity::getAssetId, assetId)
                .orderByDesc(DiscussionThreadEntity::getCreatedAt)
                .last("LIMIT " + (limit + 1));
        if (cursor != null && !cursor.isBlank()) {
            wrapper.lt(DiscussionThreadEntity::getCreatedAt, Instant.parse(cursor));
        }
        List<DiscussionThreadEntity> entities = threadMapper.selectList(wrapper);
        boolean hasMore = entities.size() > limit;
        List<DiscussionThread> items = entities.stream().limit(limit).map(this::toThread).toList();
        String nextCursor = hasMore && !items.isEmpty()
                ? items.get(items.size() - 1).createdAt().toString() : null;
        return new CursorPage<>(items, nextCursor, hasMore);
    }

    @Override
    public void insertComment(Comment comment) {
        CommentEntity entity = new CommentEntity();
        entity.setCommentId(comment.commentId());
        entity.setThreadId(comment.threadId());
        entity.setParentId(comment.parentId());
        entity.setAssetId(comment.assetId());
        entity.setBody(comment.body());
        entity.setCreatedBy(comment.createdBy());
        entity.setStatus(comment.status().name());
        entity.setRevisionCount(comment.revisionCount());
        entity.setCreatedAt(comment.createdAt());
        entity.setUpdatedAt(comment.updatedAt());
        commentMapper.insert(entity);
    }

    @Override
    public Optional<Comment> findComment(String commentId) {
        CommentEntity entity = commentMapper.selectOne(
                Wrappers.<CommentEntity>lambdaQuery()
                        .eq(CommentEntity::getCommentId, commentId));
        return entity == null ? Optional.empty() : Optional.of(toComment(entity));
    }

    @Override
    public void updateComment(Comment comment) {
        commentMapper.update(null, Wrappers.<CommentEntity>lambdaUpdate()
                .eq(CommentEntity::getCommentId, comment.commentId())
                .set(CommentEntity::getBody, comment.body())
                .set(CommentEntity::getStatus, comment.status().name())
                .set(CommentEntity::getRevisionCount, comment.revisionCount())
                .set(CommentEntity::getUpdatedAt, comment.updatedAt()));
    }

    @Override
    public List<Comment> listCommentsByThread(String threadId, String cursor, int limit) {
        var wrapper = Wrappers.<CommentEntity>lambdaQuery()
                .eq(CommentEntity::getThreadId, threadId)
                .orderByAsc(CommentEntity::getCreatedAt)
                .last("LIMIT " + (limit + 1));
        if (cursor != null && !cursor.isBlank()) {
            wrapper.gt(CommentEntity::getCreatedAt, Instant.parse(cursor));
        }
        return commentMapper.selectList(wrapper).stream().limit(limit)
                .map(this::toComment).toList();
    }

    @Override
    public void insertRevision(String revisionId, String commentId, String body, String createdBy) {
        CommentRevisionEntity entity = new CommentRevisionEntity();
        entity.setRevisionId(revisionId);
        entity.setCommentId(commentId);
        entity.setBody(body);
        entity.setCreatedBy(createdBy);
        entity.setCreatedAt(Instant.now());
        revisionMapper.insert(entity);
    }

    private DiscussionThread toThread(DiscussionThreadEntity entity) {
        return new DiscussionThread(
                entity.getThreadId(), entity.getAssetId(), entity.getTitle(),
                entity.getCreatedBy(), ThreadStatus.valueOf(entity.getStatus()),
                entity.getCommentCount() != null ? entity.getCommentCount() : 0,
                entity.getLastCommentAt(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private Comment toComment(CommentEntity entity) {
        return new Comment(
                entity.getCommentId(), entity.getThreadId(), entity.getParentId(),
                entity.getAssetId(), entity.getBody(), entity.getCreatedBy(),
                CommentStatus.valueOf(entity.getStatus()),
                entity.getRevisionCount() != null ? entity.getRevisionCount() : 0,
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
