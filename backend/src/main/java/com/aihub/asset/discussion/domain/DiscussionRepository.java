package com.aihub.asset.discussion.domain;

import com.aihub.shared.api.CursorPage;
import java.util.List;
import java.util.Optional;

/**
 * 讨论仓储端口。
 */
public interface DiscussionRepository {

    void insertThread(DiscussionThread thread);

    Optional<DiscussionThread> findThread(String threadId);

    void updateThread(DiscussionThread thread);

    CursorPage<DiscussionThread> listThreadsByAsset(String assetId, String cursor, int limit);

    void insertComment(Comment comment);

    Optional<Comment> findComment(String commentId);

    void updateComment(Comment comment);

    List<Comment> listCommentsByThread(String threadId, String cursor, int limit);

    void insertRevision(String revisionId, String commentId, String body, String createdBy);
}
