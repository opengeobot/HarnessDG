package com.aihub.asset.discussion.infrastructure;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("asset_discussion")
public class DiscussionThreadEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("thread_id")
    private String threadId;
    @TableField("asset_id")
    private String assetId;
    @TableField("title")
    private String title;
    @TableField("created_by")
    private String createdBy;
    @TableField("status")
    private String status;
    @TableField("comment_count")
    private Integer commentCount;
    @TableField("last_comment_at")
    private Instant lastCommentAt;
    @TableField("created_at")
    private Instant createdAt;
    @TableField("updated_at")
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getThreadId() { return threadId; }
    public void setThreadId(String threadId) { this.threadId = threadId; }
    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getCommentCount() { return commentCount; }
    public void setCommentCount(Integer commentCount) { this.commentCount = commentCount; }
    public Instant getLastCommentAt() { return lastCommentAt; }
    public void setLastCommentAt(Instant lastCommentAt) { this.lastCommentAt = lastCommentAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
