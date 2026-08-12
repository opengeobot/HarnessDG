package com.modelhub.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/** 资源类型注册表（03 §2.2）：扩展注册表，禁止硬编码类型列表。 */
@Entity
@Table(name = "resource_types")
public class ResourceTypeEntity {

    @Id
    @Column(name = "type_key")
    private String typeKey;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "current_schema_version")
    private Integer currentSchemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "capabilities", nullable = false)
    private String capabilities;

    @Column(name = "handler_key", nullable = false)
    private String handlerKey;

    @Column(name = "renderer_key", nullable = false)
    private String rendererKey;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public String getTypeKey() { return typeKey; }
    public void setTypeKey(String typeKey) { this.typeKey = typeKey; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public Integer getCurrentSchemaVersion() { return currentSchemaVersion; }
    public void setCurrentSchemaVersion(Integer v) { this.currentSchemaVersion = v; }
    public String getCapabilities() { return capabilities; }
    public void setCapabilities(String capabilities) { this.capabilities = capabilities; }
    public String getHandlerKey() { return handlerKey; }
    public void setHandlerKey(String handlerKey) { this.handlerKey = handlerKey; }
    public String getRendererKey() { return rendererKey; }
    public void setRendererKey(String rendererKey) { this.rendererKey = rendererKey; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
