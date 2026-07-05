package com.aihub.asset.discussion.application;

import com.aihub.asset.discussion.domain.Comment;
import com.aihub.asset.discussion.domain.CommentStatus;
import java.time.Instant;

/** 评论视图。 */
public record CommentView(String commentId, String threadId, String parentId, String assetId,
                          String body, String createdBy, CommentStatus status,
                          int revisionCount, Instant createdAt, Instant updatedAt) {

    public static CommentView from(Comment comment) {
        return new CommentView(comment.commentId(), comment.threadId(), comment.parentId(),
                comment.assetId(), comment.body(), comment.createdBy(), comment.status(),
                comment.revisionCount(), comment.createdAt(), comment.updatedAt());
    }
}
