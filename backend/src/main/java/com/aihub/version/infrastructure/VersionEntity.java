package com.aihub.version.infrastructure;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("asset_version")
public class VersionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("version_id")
    private String versionId;
    @TableField("asset_id")
    private String assetId;
    @TableField("version")
    private String version;
    @TableField("status")
    private String status;
    @TableField("source_commit")
    private String sourceCommit;
    @TableField("manifest_digest")
    private String manifestDigest;
    @TableField("git_tag")
    private String gitTag;
    @TableField("published_at")
    private Instant publishedAt;
    @TableField("published_by")
    private String publishedBy;
    @TableField("notes")
    private String notes;
    @TableField("row_version")
    private Long rowVersion;
    @TableField("created_by")
    private String createdBy;
    @TableField("created_at")
    private Instant createdAt;
    @TableField("updated_at")
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getVersionId() { return versionId; }
    public void setVersionId(String versionId) { this.versionId = versionId; }
    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSourceCommit() { return sourceCommit; }
    public void setSourceCommit(String sourceCommit) { this.sourceCommit = sourceCommit; }
    public String getManifestDigest() { return manifestDigest; }
    public void setManifestDigest(String manifestDigest) { this.manifestDigest = manifestDigest; }
    public String getGitTag() { return gitTag; }
    public void setGitTag(String gitTag) { this.gitTag = gitTag; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public String getPublishedBy() { return publishedBy; }
    public void setPublishedBy(String publishedBy) { this.publishedBy = publishedBy; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Long getRowVersion() { return rowVersion; }
    public void setRowVersion(Long rowVersion) { this.rowVersion = rowVersion; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
