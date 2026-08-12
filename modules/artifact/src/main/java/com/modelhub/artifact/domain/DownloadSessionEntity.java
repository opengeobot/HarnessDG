package com.modelhub.artifact.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 下载会话（04 §6.5）：签发即下载计数事实；(repository_id, idempotency_key) 幂等去重。 */
@Entity
@Table(name = "download_sessions")
public class DownloadSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "repository_id", nullable = false)
    private Long repositoryId;

    @Column(name = "file_version_id", nullable = false)
    private Long fileVersionId;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "issued_at", nullable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public void setPublicId(UUID publicId) { this.publicId = publicId; }
    public Long getRepositoryId() { return repositoryId; }
    public void setRepositoryId(Long repositoryId) { this.repositoryId = repositoryId; }
    public Long getFileVersionId() { return fileVersionId; }
    public void setFileVersionId(Long fileVersionId) { this.fileVersionId = fileVersionId; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public OffsetDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(OffsetDateTime issuedAt) { this.issuedAt = issuedAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
}
