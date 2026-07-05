package com.aihub.asset.discussion.domain;

import java.time.Instant;

/**
 * 评论实体。
 */
public final class Comment {

    public static final int MAX_BODY_LENGTH = 10_000;

    private final String commentId;
    private final String threadId;
    private final String parentId;
    private final String assetId;
    private String body;
    private final String createdBy;
    private CommentStatus status;
    private int revisionCount;
    private final Instant createdAt;
    private Instant updatedAt;

    public Comment(String commentId, String threadId, String parentId, String assetId,
                   String body, String createdBy, CommentStatus status, int revisionCount,
                   Instant createdAt, Instant updatedAt) {
        this.commentId = commentId;
        this.threadId = threadId;
        this.parentId = parentId;
        this.assetId = assetId;
        this.body = body;
        this.createdBy = createdBy;
        this.status = status;
        this.revisionCount = revisionCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** 编辑评论（保留旧版本到修订历史）。 */
    public String editBody(String newBody) {
        if (this.status != CommentStatus.VISIBLE) {
            throw new IllegalStateException("only VISIBLE comments can be edited");
        }
        String oldBody = this.body;
        this.body = newBody;
        this.revisionCount++;
        this.updatedAt = Instant.now();
        return oldBody;
    }

    /** 作者撤回评论。 */
    public void retract() {
        if (this.status != CommentStatus.VISIBLE) {
            throw new IllegalStateException("only VISIBLE comments can be retracted");
        }
        this.status = CommentStatus.RETRACTED;
        this.updatedAt = Instant.now();
    }

    /** Moderator 隐藏评论。 */
    public void hide() {
        if (this.status != CommentStatus.VISIBLE) {
            throw new IllegalStateException("only VISIBLE comments can be hidden");
        }
        this.status = CommentStatus.HIDDEN;
        this.updatedAt = Instant.now();
    }

    /** Moderator 恢复被隐藏的评论。 */
    public void unhide() {
        if (this.status != CommentStatus.HIDDEN) {
            throw new IllegalStateException("only HIDDEN comments can be unhidden");
        }
        this.status = CommentStatus.VISIBLE;
        this.updatedAt = Instant.now();
    }

    public String commentId() { return commentId; }
    public String threadId() { return threadId; }
    public String parentId() { return parentId; }
    public String assetId() { return assetId; }
    public String body() { return body; }
    public String createdBy() { return createdBy; }
    public CommentStatus status() { return status; }
    public int revisionCount() { return revisionCount; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
