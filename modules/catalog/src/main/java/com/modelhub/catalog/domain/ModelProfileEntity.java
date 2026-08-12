package com.modelhub.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 模型类型扩展（03 §3.1）：参数量保存数值+单位，展示串不参与排序。 */
@Entity
@Table(name = "model_profiles")
public class ModelProfileEntity {

    @Id
    @Column(name = "repository_id")
    private Long repositoryId;

    @Column(name = "task_value_id")
    private Long taskValueId;

    @Column(name = "architecture_value_id")
    private Long architectureValueId;

    @Column(name = "parameter_count")
    private Long parameterCount;

    @Column(name = "parameter_unit")
    private String parameterUnit;

    @Column(name = "primary_language_value_id")
    private Long primaryLanguageValueId;

    @Column(name = "api_status")
    private String apiStatus;

    @Column(nullable = false)
    private boolean deployable;

    @Column(name = "mcp_compatible", nullable = false)
    private boolean mcpCompatible;

    public Long getRepositoryId() { return repositoryId; }
    public void setRepositoryId(Long repositoryId) { this.repositoryId = repositoryId; }
    public Long getTaskValueId() { return taskValueId; }
    public void setTaskValueId(Long taskValueId) { this.taskValueId = taskValueId; }
    public Long getArchitectureValueId() { return architectureValueId; }
    public void setArchitectureValueId(Long architectureValueId) { this.architectureValueId = architectureValueId; }
    public Long getParameterCount() { return parameterCount; }
    public void setParameterCount(Long parameterCount) { this.parameterCount = parameterCount; }
    public String getParameterUnit() { return parameterUnit; }
    public void setParameterUnit(String parameterUnit) { this.parameterUnit = parameterUnit; }
    public Long getPrimaryLanguageValueId() { return primaryLanguageValueId; }
    public void setPrimaryLanguageValueId(Long primaryLanguageValueId) { this.primaryLanguageValueId = primaryLanguageValueId; }
    public String getApiStatus() { return apiStatus; }
    public void setApiStatus(String apiStatus) { this.apiStatus = apiStatus; }
    public boolean isDeployable() { return deployable; }
    public void setDeployable(boolean deployable) { this.deployable = deployable; }
    public boolean isMcpCompatible() { return mcpCompatible; }
    public void setMcpCompatible(boolean mcpCompatible) { this.mcpCompatible = mcpCompatible; }
}
