package com.modelhub.artifact.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 文件版本（05 §6.3）：content_source 二选一——git 只有 gitBlobSha，object 只有 objectBlobId。
 * 同一 (repo, branch, path) 至多一个 staging/active 版本（部分唯一索引 uq_file_version_head）。
 */
@Entity
@Table(name = "file_versions")
public class FileVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "repository_id", nullable = false)
    private Long repositoryId;

    @Column(nullable = false)
    private String branch;

    @Column(nullable = false)
    private String path;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "content_source", nullable = false)
    private String contentSource;

    @Column(name = "git_blob_sha")
    private String gitBlobSha;

    @Column(name = "object_blob_id")
    private Long objectBlobId;

    @Column(name = "commit_sha")
    private String commitSha;

    @Column(nullable = false)
    private String status;

    @Column(name = "upload_session_id")
    private Long uploadSessionId;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public void setPublicId(UUID publicId) { this.publicId = publicId; }
    public Long getRepositoryId() { return repositoryId; }
    public void setRepositoryId(Long repositoryId) { this.repositoryId = repositoryId; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getContentSource() { return contentSource; }
    public void setContentSource(String contentSource) { this.contentSource = contentSource; }
    public String getGitBlobSha() { return gitBlobSha; }
    public void setGitBlobSha(String gitBlobSha) { this.gitBlobSha = gitBlobSha; }
    public Long getObjectBlobId() { return objectBlobId; }
    public void setObjectBlobId(Long objectBlobId) { this.objectBlobId = objectBlobId; }
    public String getCommitSha() { return commitSha; }
    public void setCommitSha(String commitSha) { this.commitSha = commitSha; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getUploadSessionId() { return uploadSessionId; }
    public void setUploadSessionId(Long uploadSessionId) { this.uploadSessionId = uploadSessionId; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(Long createdByUserId) { this.createdByUserId = createdByUserId; }
    public long getVersion() { return version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
