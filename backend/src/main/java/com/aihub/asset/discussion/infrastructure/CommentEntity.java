package com.aihub.asset.discussion.infrastructure;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("asset_comment")
public class CommentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("comment_id")
    private String commentId;
    @TableField("thread_id")
    private String threadId;
    @TableField("parent_id")
    private String parentId;
    @TableField("asset_id")
    private String assetId;
    @TableField("body")
    private String body;
    @TableField("created_by")
    private String createdBy;
    @TableField("status")
    private String status;
    @TableField("revision_count")
    private Integer revisionCount;
    @TableField("created_at")
    private Instant createdAt;
    @TableField("updated_at")
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCommentId() { return commentId; }
    public void setCommentId(String commentId) { this.commentId = commentId; }
    public String getThreadId() { return threadId; }
    public void setThreadId(String threadId) { this.threadId = threadId; }
    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }
    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getRevisionCount() { return revisionCount; }
    public void setRevisionCount(Integer revisionCount) { this.revisionCount = revisionCount; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
