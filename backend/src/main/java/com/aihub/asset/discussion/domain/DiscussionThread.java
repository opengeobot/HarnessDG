package com.aihub.asset.discussion.domain;

import java.time.Instant;

/**
 * 讨论线程聚合根。
 */
public final class DiscussionThread {

    private final String threadId;
    private final String assetId;
    private final String title;
    private final String createdBy;
    private ThreadStatus status;
    private int commentCount;
    private Instant lastCommentAt;
    private final Instant createdAt;
    private Instant updatedAt;

    public DiscussionThread(String threadId, String assetId, String title, String createdBy,
                            ThreadStatus status, int commentCount, Instant lastCommentAt,
                            Instant createdAt, Instant updatedAt) {
        this.threadId = threadId;
        this.assetId = assetId;
        this.title = title;
        this.createdBy = createdBy;
        this.status = status;
        this.commentCount = commentCount;
        this.lastCommentAt = lastCommentAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void lock() {
        if (this.status != ThreadStatus.OPEN) {
            throw new IllegalStateException("only OPEN threads can be locked, current: " + this.status);
        }
        this.status = ThreadStatus.LOCKED;
        this.updatedAt = Instant.now();
    }

    public void unlock() {
        if (this.status != ThreadStatus.LOCKED) {
            throw new IllegalStateException("only LOCKED threads can be unlocked, current: " + this.status);
        }
        this.status = ThreadStatus.OPEN;
        this.updatedAt = Instant.now();
    }

    public void incrementCommentCount() {
        this.commentCount++;
        this.lastCommentAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String threadId() { return threadId; }
    public String assetId() { return assetId; }
    public String title() { return title; }
    public String createdBy() { return createdBy; }
    public ThreadStatus status() { return status; }
    public int commentCount() { return commentCount; }
    public Instant lastCommentAt() { return lastCommentAt; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
