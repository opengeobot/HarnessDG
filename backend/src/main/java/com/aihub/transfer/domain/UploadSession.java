package com.aihub.transfer.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * 上传会话聚合根。
 *
 * <p>追踪 Multipart 上传生命周期：OPEN → COMMITTING → PROCESSING → COMPLETED/FAILED，
 * 或 CANCELLED/EXPIRED。
 * 单会话限额 20GiB，超过应引导使用 CLI/DVC。
 */
public final class UploadSession {

    /** 单会话最大字节数：20GiB。 */
    public static final long MAX_SESSION_BYTES = 20L * 1024 * 1024 * 1024;

    private final String sessionId;
    private final String assetId;
    private final String versionId;
    private final String principalId;
    private UploadSessionStatus status;
    private long totalBytes;
    private int fileCount;
    private final Instant expiresAt;
    private String minioUploadId;
    private final Instant createdAt;
    private Instant updatedAt;

    public UploadSession(String sessionId, String assetId, String versionId,
                         String principalId, UploadSessionStatus status,
                         long totalBytes, int fileCount, Instant expiresAt,
                         String minioUploadId, Instant createdAt, Instant updatedAt) {
        this.sessionId = Objects.requireNonNull(sessionId);
        this.assetId = Objects.requireNonNull(assetId);
        this.versionId = Objects.requireNonNull(versionId);
        this.principalId = Objects.requireNonNull(principalId);
        this.status = Objects.requireNonNull(status);
        this.totalBytes = totalBytes;
        this.fileCount = fileCount;
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.minioUploadId = minioUploadId;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    /** 创建新上传会话。 */
    public static UploadSession create(String sessionId, String assetId, String versionId,
                                       String principalId, long totalBytes, int fileCount,
                                       Instant expiresAt) {
        if (totalBytes > MAX_SESSION_BYTES) {
            throw new IllegalArgumentException(
                    "session totalBytes " + totalBytes + " exceeds limit " + MAX_SESSION_BYTES
                            + " (20GiB); use CLI/DVC for large uploads");
        }
        if (totalBytes < 0) {
            throw new IllegalArgumentException("totalBytes must be non-negative");
        }
        if (fileCount < 0) {
            throw new IllegalArgumentException("fileCount must be non-negative");
        }
        Instant now = Instant.now();
        return new UploadSession(sessionId, assetId, versionId, principalId,
                UploadSessionStatus.OPEN, totalBytes, fileCount, expiresAt, null, now, now);
    }

    /** 绑定 MinIO uploadId。 */
    public void bindMinioUploadId(String uploadId) {
        this.minioUploadId = uploadId;
        this.updatedAt = Instant.now();
    }

    /** 进入 COMMITTING 状态。 */
    public void commit() {
        if (this.status != UploadSessionStatus.OPEN) {
            throw new IllegalStateException(
                    "only OPEN sessions can commit, current: " + this.status);
        }
        this.status = UploadSessionStatus.COMMITTING;
        this.updatedAt = Instant.now();
    }

    /** 进入 PROCESSING 状态（Multipart 已完成，等待物化 Job）。 */
    public void markProcessing() {
        if (this.status != UploadSessionStatus.COMMITTING) {
            throw new IllegalStateException(
                    "only COMMITTING sessions can enter PROCESSING, current: " + this.status);
        }
        this.status = UploadSessionStatus.PROCESSING;
        this.updatedAt = Instant.now();
    }

    /** 标记物化完成。 */
    public void complete() {
        if (this.status != UploadSessionStatus.PROCESSING && this.status != UploadSessionStatus.COMMITTING) {
            throw new IllegalStateException(
                    "only PROCESSING or COMMITTING sessions can complete, current: " + this.status);
        }
        this.status = UploadSessionStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    /** 标记物化失败。 */
    public void markFailed() {
        if (this.status != UploadSessionStatus.PROCESSING) {
            throw new IllegalStateException(
                    "only PROCESSING sessions can fail, current: " + this.status);
        }
        this.status = UploadSessionStatus.FAILED;
        this.updatedAt = Instant.now();
    }

    /** 取消会话。 */
    public void cancel() {
        if (this.status == UploadSessionStatus.COMPLETED
                || this.status == UploadSessionStatus.CANCELLED
                || this.status == UploadSessionStatus.PROCESSING) {
            throw new IllegalStateException(
                    "cannot cancel session in status: " + this.status);
        }
        this.status = UploadSessionStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    /** 检查会话是否已过期。 */
    public boolean isExpired() {
        return Instant.now().isAfter(this.expiresAt);
    }

    // ---- accessors ----

    public String sessionId() { return sessionId; }
    public String assetId() { return assetId; }
    public String versionId() { return versionId; }
    public String principalId() { return principalId; }
    public UploadSessionStatus status() { return status; }
    public long totalBytes() { return totalBytes; }
    public int fileCount() { return fileCount; }
    public Instant expiresAt() { return expiresAt; }
    public String minioUploadId() { return minioUploadId; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
