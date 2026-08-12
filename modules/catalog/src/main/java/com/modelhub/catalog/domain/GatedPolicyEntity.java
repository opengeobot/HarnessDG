package com.modelhub.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.OffsetDateTime;

/** gated 策略（03 §7）：唯一持久化真相源；generation 递增使旧 grant 永不复活。 */
@Entity
@Table(name = "repository_gated_policies")
public class GatedPolicyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private long generation;

    @Column(name = "default_grant_ttl_seconds", nullable = false)
    private long defaultGrantTtlSeconds;

    @Column(name = "request_schema_version", nullable = false)
    private int requestSchemaVersion;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getGeneration() { return generation; }
    public void setGeneration(long generation) { this.generation = generation; }
    public long getDefaultGrantTtlSeconds() { return defaultGrantTtlSeconds; }
    public void setDefaultGrantTtlSeconds(long defaultGrantTtlSeconds) { this.defaultGrantTtlSeconds = defaultGrantTtlSeconds; }
    public int getRequestSchemaVersion() { return requestSchemaVersion; }
    public void setRequestSchemaVersion(int requestSchemaVersion) { this.requestSchemaVersion = requestSchemaVersion; }
    public Long getVersion() { return version; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
