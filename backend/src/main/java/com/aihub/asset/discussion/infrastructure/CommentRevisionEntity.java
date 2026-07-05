package com.aihub.asset.discussion.infrastructure;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("asset_comment_revision")
public class CommentRevisionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("revision_id")
    private String revisionId;
    @TableField("comment_id")
    private String commentId;
    @TableField("body")
    private String body;
    @TableField("created_by")
    private String createdBy;
    @TableField("created_at")
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRevisionId() { return revisionId; }
    public void setRevisionId(String revisionId) { this.revisionId = revisionId; }
    public String getCommentId() { return commentId; }
    public void setCommentId(String commentId) { this.commentId = commentId; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
