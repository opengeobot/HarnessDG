/*
 * 功能: Agent 身份领域聚合，承载凭据摘要、敏感等级、Scope、Tool 白名单与启用/禁用行为。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.domain;

import com.aihub.shared.security.PasswordHasher;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Agent 身份领域聚合。
 *
 * <p>封装凭据校验、启用/禁用、Tool 白名单替换等行为；只持有凭据哈希摘要，绝不持有明文。
 */
public final class AgentIdentity {

    private final String agentId;
    private final String principalId;
    private String displayName;
    private final String agentType;
    private final String vendor;
    private String credentialHash;
    private String credentialAlgorithm;
    private int maxSensitivityLevel;
    private final Set<String> scopes;
    private List<String> toolAllowlist;
    private long tokenVersion;
    private AgentStatus status;
    private long rowVersion;
    private Instant createdAt;
    private Instant updatedAt;

    private AgentIdentity(Builder builder) {
        this.agentId = builder.agentId;
        this.principalId = builder.principalId;
        this.displayName = builder.displayName;
        this.agentType = builder.agentType;
        this.vendor = builder.vendor;
        this.credentialHash = builder.credentialHash;
        this.credentialAlgorithm = builder.credentialAlgorithm == null ? "BCRYPT" : builder.credentialAlgorithm;
        this.maxSensitivityLevel = builder.maxSensitivityLevel;
        this.scopes = new LinkedHashSet<>(builder.scopes == null ? Set.of() : builder.scopes);
        this.toolAllowlist = List.copyOf(builder.toolAllowlist == null ? List.of() : builder.toolAllowlist);
        this.tokenVersion = builder.tokenVersion;
        this.status = builder.status == null ? AgentStatus.ACTIVE : builder.status;
        this.rowVersion = builder.rowVersion;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    /**
     * @return Agent 是否被禁用
     */
    public boolean isDisabled() {
        return status == AgentStatus.DISABLED;
    }

    /**
     * 校验凭据是否匹配（不改变状态）。
     */
    public boolean matchesCredential(CharSequence rawCredential, PasswordHasher hasher) {
        return hasher.matches(rawCredential, credentialHash);
    }

    /**
     * 禁用 Agent：递增 Token 版本以使其凭据/Token 立即失效。
     */
    public void disable(Instant now) {
        this.status = AgentStatus.DISABLED;
        this.tokenVersion += 1;
        touch(now);
    }

    /**
     * 启用 Agent。
     */
    public void enable(Instant now) {
        this.status = AgentStatus.ACTIVE;
        touch(now);
    }

    /**
     * 替换 MCP Tool 白名单。
     */
    public void replaceToolAllowlist(List<String> tools, Instant now) {
        this.toolAllowlist = List.copyOf(tools == null ? List.of() : tools);
        touch(now);
    }

    private void touch(Instant now) {
        this.updatedAt = now;
    }

    public String agentId() {
        return agentId;
    }

    public String principalId() {
        return principalId;
    }

    public String displayName() {
        return displayName;
    }

    public String agentType() {
        return agentType;
    }

    public String vendor() {
        return vendor;
    }

    public String credentialHash() {
        return credentialHash;
    }

    public String credentialAlgorithm() {
        return credentialAlgorithm;
    }

    public int maxSensitivityLevel() {
        return maxSensitivityLevel;
    }

    public Set<String> scopes() {
        return Set.copyOf(scopes);
    }

    public List<String> toolAllowlist() {
        return toolAllowlist;
    }

    public long tokenVersion() {
        return tokenVersion;
    }

    public AgentStatus status() {
        return status;
    }

    public long rowVersion() {
        return rowVersion;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /**
     * 构建器。
     */
    public static final class Builder {
        private String agentId;
        private String principalId;
        private String displayName;
        private String agentType;
        private String vendor;
        private String credentialHash;
        private String credentialAlgorithm;
        private int maxSensitivityLevel;
        private Set<String> scopes;
        private List<String> toolAllowlist;
        private long tokenVersion;
        private AgentStatus status;
        private long rowVersion;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public Builder principalId(String principalId) {
            this.principalId = principalId;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder agentType(String agentType) {
            this.agentType = agentType;
            return this;
        }

        public Builder vendor(String vendor) {
            this.vendor = vendor;
            return this;
        }

        public Builder credentialHash(String credentialHash) {
            this.credentialHash = credentialHash;
            return this;
        }

        public Builder credentialAlgorithm(String credentialAlgorithm) {
            this.credentialAlgorithm = credentialAlgorithm;
            return this;
        }

        public Builder maxSensitivityLevel(int maxSensitivityLevel) {
            this.maxSensitivityLevel = maxSensitivityLevel;
            return this;
        }

        public Builder scopes(Set<String> scopes) {
            this.scopes = scopes;
            return this;
        }

        public Builder toolAllowlist(List<String> toolAllowlist) {
            this.toolAllowlist = toolAllowlist;
            return this;
        }

        public Builder tokenVersion(long tokenVersion) {
            this.tokenVersion = tokenVersion;
            return this;
        }

        public Builder status(AgentStatus status) {
            this.status = status;
            return this;
        }

        public Builder rowVersion(long rowVersion) {
            this.rowVersion = rowVersion;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public AgentIdentity build() {
            if (agentId == null || principalId == null || agentType == null || credentialHash == null) {
                throw new IllegalArgumentException("agentId/principalId/agentType/credentialHash are required");
            }
            return new AgentIdentity(this);
        }
    }
}
