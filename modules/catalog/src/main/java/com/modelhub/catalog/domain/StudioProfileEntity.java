package com.modelhub.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 创空间类型扩展（03 §3.3）：v1 不交付真实运行时，publish_status 仅 draft/metadata_only。 */
@Entity
@Table(name = "studio_profiles")
public class StudioProfileEntity {

    @Id
    @Column(name = "repository_id")
    private Long repositoryId;

    @Column(name = "publish_status", nullable = false)
    private String publishStatus;

    @Column(name = "runtime_type")
    private String runtimeType;

    @Column(nullable = false)
    private boolean deployable;

    @Column(name = "mcp_compatible", nullable = false)
    private boolean mcpCompatible;

    @Column(name = "cover_object_id")
    private Long coverObjectId;

    public Long getRepositoryId() { return repositoryId; }
    public void setRepositoryId(Long repositoryId) { this.repositoryId = repositoryId; }
    public String getPublishStatus() { return publishStatus; }
    public void setPublishStatus(String publishStatus) { this.publishStatus = publishStatus; }
    public String getRuntimeType() { return runtimeType; }
    public void setRuntimeType(String runtimeType) { this.runtimeType = runtimeType; }
    public boolean isDeployable() { return deployable; }
    public void setDeployable(boolean deployable) { this.deployable = deployable; }
    public boolean isMcpCompatible() { return mcpCompatible; }
    public void setMcpCompatible(boolean mcpCompatible) { this.mcpCompatible = mcpCompatible; }
    public Long getCoverObjectId() { return coverObjectId; }
    public void setCoverObjectId(Long coverObjectId) { this.coverObjectId = coverObjectId; }
}
