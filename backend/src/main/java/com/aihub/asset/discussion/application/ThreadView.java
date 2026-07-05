package com.aihub.asset.discussion.application;

import com.aihub.asset.discussion.domain.Comment;
import com.aihub.asset.discussion.domain.CommentStatus;
import com.aihub.asset.discussion.domain.DiscussionThread;
import com.aihub.asset.discussion.domain.ThreadStatus;
import java.time.Instant;

/** 讨论线程视图。 */
public record ThreadView(String threadId, String assetId, String title, String createdBy,
                         ThreadStatus status, int commentCount, Instant lastCommentAt,
                         Instant createdAt) {

    public static ThreadView from(DiscussionThread thread) {
        return new ThreadView(thread.threadId(), thread.assetId(), thread.title(),
                thread.createdBy(), thread.status(), thread.commentCount(),
                thread.lastCommentAt(), thread.createdAt());
    }
}
