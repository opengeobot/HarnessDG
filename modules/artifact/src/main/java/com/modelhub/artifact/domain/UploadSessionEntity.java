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
 * 上传会话（05 §5/§6）：十一态状态机 initiated→uploading→verifying→scanning→committing→completed，
 * 旁路 conflict/aborting/aborted/expired/failed。对象键服务端预留 objects/{publicId}。
 */
@Entity
@Table(name = "upload_sessions")
public class UploadSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "repository_id", nullable = false)
    private Long repositoryId;

    @Column(nullable = false)
    private String branch;

    @Column(name = "base_commit_sha", nullable = false)
    private String baseCommitSha;

    @Column(nullable = false)
    private String path;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "claimed_sha256")
    private String claimedSha256;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "content_source", nullable = false)
    private String contentSource;

    @Column(nullable = false)
    private String status;

    @Column(name = "part_size", nullable = false)
    private long partSize;

    @Column(name = "max_concurrency", nullable = false)
    private int maxConcurrency;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "provider_upload_id")
    private String providerUploadId;

    @Column(name = "object_key", nullable = false)
    private String objectKey;

    @Column(name = "verified_sha256")
    private String verifiedSha256;

    @Column(name = "current_branch_head")
    private String currentBranchHead;

    @Column(name = "conflict_resolution", nullable = false)
    private String conflictResolution;

    @Column(name = "file_version_id")
    private Long fileVersionId;

    @Column(name = "last_error")
    private String lastError;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

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
    public String getBaseCommitSha() { return baseCommitSha; }
    public void setBaseCommitSha(String baseCommitSha) { this.baseCommitSha = baseCommitSha; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getClaimedSha256() { return claimedSha256; }
    public void setClaimedSha256(String claimedSha256) { this.claimedSha256 = claimedSha256; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getContentSource() { return contentSource; }
    public void setContentSource(String contentSource) { this.contentSource = contentSource; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getPartSize() { return partSize; }
    public void setPartSize(long partSize) { this.partSize = partSize; }
    public int getMaxConcurrency() { return maxConcurrency; }
    public void setMaxConcurrency(int maxConcurrency) { this.maxConcurrency = maxConcurrency; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    public String getProviderUploadId() { return providerUploadId; }
    public void setProviderUploadId(String providerUploadId) { this.providerUploadId = providerUploadId; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public String getVerifiedSha256() { return verifiedSha256; }
    public void setVerifiedSha256(String verifiedSha256) { this.verifiedSha256 = verifiedSha256; }
    public String getCurrentBranchHead() { return currentBranchHead; }
    public void setCurrentBranchHead(String currentBranchHead) { this.currentBranchHead = currentBranchHead; }
    public String getConflictResolution() { return conflictResolution; }
    public void setConflictResolution(String conflictResolution) { this.conflictResolution = conflictResolution; }
    public Long getFileVersionId() { return fileVersionId; }
    public void setFileVersionId(Long fileVersionId) { this.fileVersionId = fileVersionId; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public long getVersion() { return version; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(Long createdByUserId) { this.createdByUserId = createdByUserId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
