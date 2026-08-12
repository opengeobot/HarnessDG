package com.modelhub.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** Git 绑定投影（03 §5.1）：repositories 不保存 Provider 专属列。 */
@Entity
@Table(name = "repository_git_bindings")
public class GitBindingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repository_id", nullable = false, unique = true)
    private Long repositoryId;

    @Column(nullable = false)
    private String provider;

    @Column(name = "external_repository_id", nullable = false)
    private String externalRepositoryId;

    @Column(name = "external_namespace", nullable = false)
    private String externalNamespace;

    @Column(name = "external_name", nullable = false)
    private String externalName;

    @Column(name = "object_format", nullable = false)
    private String objectFormat;

    @Column(name = "sync_status", nullable = false)
    private String syncStatus;

    @Column(name = "last_synced_at")
    private OffsetDateTime lastSyncedAt;

    @Column(name = "last_error")
    private String lastError;

    public Long getId() { return id; }
    public Long getRepositoryId() { return repositoryId; }
    public void setRepositoryId(Long repositoryId) { this.repositoryId = repositoryId; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getExternalRepositoryId() { return externalRepositoryId; }
    public void setExternalRepositoryId(String externalRepositoryId) { this.externalRepositoryId = externalRepositoryId; }
    public String getExternalNamespace() { return externalNamespace; }
    public void setExternalNamespace(String externalNamespace) { this.externalNamespace = externalNamespace; }
    public String getExternalName() { return externalName; }
    public void setExternalName(String externalName) { this.externalName = externalName; }
    public String getObjectFormat() { return objectFormat; }
    public void setObjectFormat(String objectFormat) { this.objectFormat = objectFormat; }
    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }
    public OffsetDateTime getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(OffsetDateTime lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
