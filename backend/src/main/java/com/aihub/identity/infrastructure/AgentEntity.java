/*
 * 功能: Agent 持久化实体，映射 iam_agent 表。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.infrastructure;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.List;

/**
 * Agent 持久化实体（{@code iam_agent} 表）。
 *
 * <p>{@code scopes}/{@code toolAllowlist} 经 {@link JsonbStringListTypeHandler} 与 jsonb 列互转。
 * {@code credentialHash} 仅库内使用，绝不跨模块外泄。
 */
@TableName(value = "iam_agent", autoResultMap = true)
public class AgentEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("agent_id")
    private String agentId;

    @TableField("principal_id")
    private String principalId;

    @TableField("display_name")
    private String displayName;

    @TableField("agent_type")
    private String agentType;

    @TableField("vendor")
    private String vendor;

    @TableField("credential_hash")
    private String credentialHash;

    @TableField("credential_algorithm")
    private String credentialAlgorithm;

    @TableField("max_sensitivity_level")
    private Integer maxSensitivityLevel;

    @TableField(value = "scopes", typeHandler = JsonbStringListTypeHandler.class)
    private List<String> scopes;

    @TableField(value = "tool_allowlist", typeHandler = JsonbStringListTypeHandler.class)
    private List<String> toolAllowlist;

    @TableField("token_version")
    private Long tokenVersion;

    @TableField("status")
    private String status;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;

    @TableField("row_version")
    private Long rowVersion;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getPrincipalId() {
        return principalId;
    }

    public void setPrincipalId(String principalId) {
        this.principalId = principalId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getAgentType() {
        return agentType;
    }

    public void setAgentType(String agentType) {
        this.agentType = agentType;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public String getCredentialHash() {
        return credentialHash;
    }

    public void setCredentialHash(String credentialHash) {
        this.credentialHash = credentialHash;
    }

    public String getCredentialAlgorithm() {
        return credentialAlgorithm;
    }

    public void setCredentialAlgorithm(String credentialAlgorithm) {
        this.credentialAlgorithm = credentialAlgorithm;
    }

    public Integer getMaxSensitivityLevel() {
        return maxSensitivityLevel;
    }

    public void setMaxSensitivityLevel(Integer maxSensitivityLevel) {
        this.maxSensitivityLevel = maxSensitivityLevel;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public void setScopes(List<String> scopes) {
        this.scopes = scopes;
    }

    public List<String> getToolAllowlist() {
        return toolAllowlist;
    }

    public void setToolAllowlist(List<String> toolAllowlist) {
        this.toolAllowlist = toolAllowlist;
    }

    public Long getTokenVersion() {
        return tokenVersion;
    }

    public void setTokenVersion(Long tokenVersion) {
        this.tokenVersion = tokenVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(Long rowVersion) {
        this.rowVersion = rowVersion;
    }
}
