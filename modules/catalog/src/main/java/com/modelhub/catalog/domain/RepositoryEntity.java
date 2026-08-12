package com.modelhub.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 仓库聚合根（03 §2.3）：所有权来自 namespace；created_by 仅审计；
 * lifecycle 9 态 + restore_target_status；gated 真相源为 gated_policy_id。
 */
@Entity
@Table(name = "repositories")
public class RepositoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "namespace_id", nullable = false)
    private Long namespaceId;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(nullable = false)
    private String name;

    @Column(name = "normalized_name", nullable = false, columnDefinition = "citext")
    private String normalizedName;

    @Column(name = "display_name")
    private String displayName;

    private String description;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(nullable = false)
    private String visibility;

    @Column(name = "gated_policy_id")
    private Long gatedPolicyId;

    @Column(name = "lifecycle_status", nullable = false)
    private String lifecycleStatus;

    @Column(name = "restore_target_status")
    private String restoreTargetStatus;

    @Column(name = "retention_until")
    private OffsetDateTime retentionUntil;

    @Column(name = "metadata_schema_version", nullable = false)
    private Integer metadataSchemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String metadata;

    @Column(name = "default_branch", nullable = false)
    private String defaultBranch;

    @Column(name = "latest_commit_sha")
    private String latestCommitSha;

    @Column(name = "provision_retry_count", nullable = false)
    private int provisionRetryCount;

    @Column(name = "provision_next_retry_at")
    private OffsetDateTime provisionNextRetryAt;

    @Column(name = "provision_last_error")
    private String provisionLastError;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getPublicId() { return publicId; }
    public void setPublicId(UUID publicId) { this.publicId = publicId; }
    public Long getNamespaceId() { return namespaceId; }
    public void setNamespaceId(Long namespaceId) { this.namespaceId = namespaceId; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNormalizedName() { return normalizedName; }
    public void setNormalizedName(String normalizedName) { this.normalizedName = normalizedName; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(Long createdByUserId) { this.createdByUserId = createdByUserId; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public Long getGatedPolicyId() { return gatedPolicyId; }
    public void setGatedPolicyId(Long gatedPolicyId) { this.gatedPolicyId = gatedPolicyId; }
    public String getLifecycleStatus() { return lifecycleStatus; }
    public void setLifecycleStatus(String lifecycleStatus) { this.lifecycleStatus = lifecycleStatus; }
    public String getRestoreTargetStatus() { return restoreTargetStatus; }
    public void setRestoreTargetStatus(String restoreTargetStatus) { this.restoreTargetStatus = restoreTargetStatus; }
    public OffsetDateTime getRetentionUntil() { return retentionUntil; }
    public void setRetentionUntil(OffsetDateTime retentionUntil) { this.retentionUntil = retentionUntil; }
    public Integer getMetadataSchemaVersion() { return metadataSchemaVersion; }
    public void setMetadataSchemaVersion(Integer metadataSchemaVersion) { this.metadataSchemaVersion = metadataSchemaVersion; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public String getDefaultBranch() { return defaultBranch; }
    public void setDefaultBranch(String defaultBranch) { this.defaultBranch = defaultBranch; }
    public String getLatestCommitSha() { return latestCommitSha; }
    public void setLatestCommitSha(String latestCommitSha) { this.latestCommitSha = latestCommitSha; }
    public int getProvisionRetryCount() { return provisionRetryCount; }
    public void setProvisionRetryCount(int provisionRetryCount) { this.provisionRetryCount = provisionRetryCount; }
    public OffsetDateTime getProvisionNextRetryAt() { return provisionNextRetryAt; }
    public void setProvisionNextRetryAt(OffsetDateTime provisionNextRetryAt) { this.provisionNextRetryAt = provisionNextRetryAt; }
    public String getProvisionLastError() { return provisionLastError; }
    public void setProvisionLastError(String provisionLastError) { this.provisionLastError = provisionLastError; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }
}
