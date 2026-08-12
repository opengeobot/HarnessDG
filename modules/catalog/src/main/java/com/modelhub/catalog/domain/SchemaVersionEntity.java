package com.modelhub.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * 类型 Schema 版本（03 §2.2）：PRIMARY KEY(type_key,version)；
 * published 版本不可原地修改，升级插入新版本并原子切换 current_schema_version。
 */
@Entity
@Table(name = "resource_type_schema_versions")
@IdClass(SchemaVersionId.class)
public class SchemaVersionEntity {

    @Id
    @Column(name = "type_key")
    private String typeKey;

    @Id
    @Column(name = "version")
    private Integer version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_schema", nullable = false)
    private String metadataSchema;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ui_schema", nullable = false)
    private String uiSchema;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "facet_definitions", nullable = false)
    private String facetDefinitions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "file_policy", nullable = false)
    private String filePolicy;

    @Column(name = "default_visibility", nullable = false)
    private String defaultVisibility;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_workflows", nullable = false)
    private String allowedWorkflows;

    @Column(nullable = false)
    private String checksum;

    @Column(nullable = false)
    private String status;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public String getTypeKey() { return typeKey; }
    public void setTypeKey(String typeKey) { this.typeKey = typeKey; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getMetadataSchema() { return metadataSchema; }
    public void setMetadataSchema(String metadataSchema) { this.metadataSchema = metadataSchema; }
    public String getUiSchema() { return uiSchema; }
    public void setUiSchema(String uiSchema) { this.uiSchema = uiSchema; }
    public String getFacetDefinitions() { return facetDefinitions; }
    public void setFacetDefinitions(String facetDefinitions) { this.facetDefinitions = facetDefinitions; }
    public String getFilePolicy() { return filePolicy; }
    public void setFilePolicy(String filePolicy) { this.filePolicy = filePolicy; }
    public String getDefaultVisibility() { return defaultVisibility; }
    public void setDefaultVisibility(String defaultVisibility) { this.defaultVisibility = defaultVisibility; }
    public String getAllowedWorkflows() { return allowedWorkflows; }
    public void setAllowedWorkflows(String allowedWorkflows) { this.allowedWorkflows = allowedWorkflows; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
