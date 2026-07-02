/*
 * 功能: Agent 身份仓储适配器，基于 MyBatis-Plus 实现 Agent 与主体的持久化与查询。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.infrastructure;

import com.aihub.identity.domain.AgentIdentity;
import com.aihub.identity.domain.AgentIdentityRepository;
import com.aihub.identity.domain.AgentStatus;
import com.aihub.identity.domain.PrincipalAccount;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agent 身份仓储适配器。负责领域聚合与持久化实体互转，状态更新走 row_version 乐观锁。
 */
@Repository
public class MyBatisAgentIdentityRepository implements AgentIdentityRepository {

    /** UpdateWrapper.set 不经实体 typeHandler，故对 jsonb 列显式指定处理器。 */
    private static final String JSONB_LIST_HANDLER =
            "typeHandler=com.aihub.identity.infrastructure.JsonbStringListTypeHandler";

    private final AgentMapper agentMapper;
    private final PrincipalMapper principalMapper;

    public MyBatisAgentIdentityRepository(AgentMapper agentMapper, PrincipalMapper principalMapper) {
        this.agentMapper = agentMapper;
        this.principalMapper = principalMapper;
    }

    @Override
    @Transactional
    public void create(PrincipalAccount principal, AgentIdentity agent) {
        principalMapper.insert(toPrincipalEntity(principal));
        agentMapper.insert(toEntity(agent));
    }

    @Override
    @Transactional
    public boolean update(AgentIdentity agent) {
        long currentVersion = agent.rowVersion();
        int affected = agentMapper.update(null, Wrappers.<AgentEntity>lambdaUpdate()
                .eq(AgentEntity::getAgentId, agent.agentId())
                .eq(AgentEntity::getRowVersion, currentVersion)
                .set(AgentEntity::getDisplayName, agent.displayName())
                .set(AgentEntity::getMaxSensitivityLevel, agent.maxSensitivityLevel())
                .set(AgentEntity::getToolAllowlist, List.copyOf(agent.toolAllowlist()), JSONB_LIST_HANDLER)
                .set(AgentEntity::getTokenVersion, agent.tokenVersion())
                .set(AgentEntity::getStatus, agent.status().name())
                .set(AgentEntity::getUpdatedAt, agent.updatedAt() == null ? Instant.now() : agent.updatedAt())
                .set(AgentEntity::getRowVersion, currentVersion + 1));
        if (affected > 0) {
            principalMapper.update(null, Wrappers.<PrincipalEntity>lambdaUpdate()
                    .eq(PrincipalEntity::getPrincipalId, agent.principalId())
                    .set(PrincipalEntity::getStatus, agent.status() == AgentStatus.DISABLED ? "DISABLED" : "ACTIVE")
                    .set(PrincipalEntity::getUpdatedAt, Instant.now()));
        }
        return affected > 0;
    }

    @Override
    public Optional<AgentIdentity> findByAgentId(String agentId) {
        return findOne(Wrappers.<AgentEntity>lambdaQuery().eq(AgentEntity::getAgentId, agentId));
    }

    @Override
    public Optional<AgentIdentity> findByPrincipalId(String principalId) {
        return findOne(Wrappers.<AgentEntity>lambdaQuery().eq(AgentEntity::getPrincipalId, principalId));
    }

    @Override
    public List<AgentIdentity> findAll() {
        return agentMapper.selectList(Wrappers.<AgentEntity>lambdaQuery()
                        .orderByDesc(AgentEntity::getCreatedAt))
                .stream().map(this::toDomain).toList();
    }

    private Optional<AgentIdentity> findOne(LambdaQueryWrapper<AgentEntity> wrapper) {
        AgentEntity entity = agentMapper.selectOne(wrapper);
        return entity == null ? Optional.empty() : Optional.of(toDomain(entity));
    }

    private PrincipalEntity toPrincipalEntity(PrincipalAccount principal) {
        PrincipalEntity entity = new PrincipalEntity();
        entity.setPrincipalId(principal.principalId());
        entity.setPrincipalType(principal.principalType().name());
        entity.setDisplayName(principal.displayName());
        entity.setStatus(principal.status());
        entity.setCreatedAt(principal.createdAt());
        entity.setUpdatedAt(principal.updatedAt());
        entity.setRowVersion(0);
        return entity;
    }

    private AgentEntity toEntity(AgentIdentity agent) {
        AgentEntity entity = new AgentEntity();
        entity.setAgentId(agent.agentId());
        entity.setPrincipalId(agent.principalId());
        entity.setDisplayName(agent.displayName());
        entity.setAgentType(agent.agentType());
        entity.setVendor(agent.vendor());
        entity.setCredentialHash(agent.credentialHash());
        entity.setCredentialAlgorithm(agent.credentialAlgorithm());
        entity.setMaxSensitivityLevel(agent.maxSensitivityLevel());
        entity.setScopes(List.copyOf(agent.scopes()));
        entity.setToolAllowlist(List.copyOf(agent.toolAllowlist()));
        entity.setTokenVersion(agent.tokenVersion());
        entity.setStatus(agent.status().name());
        entity.setCreatedAt(agent.createdAt());
        entity.setUpdatedAt(agent.updatedAt());
        entity.setRowVersion(agent.rowVersion());
        return entity;
    }

    private AgentIdentity toDomain(AgentEntity entity) {
        return new AgentIdentity.Builder()
                .agentId(entity.getAgentId())
                .principalId(entity.getPrincipalId())
                .displayName(entity.getDisplayName())
                .agentType(entity.getAgentType())
                .vendor(entity.getVendor())
                .credentialHash(entity.getCredentialHash())
                .credentialAlgorithm(entity.getCredentialAlgorithm())
                .maxSensitivityLevel(entity.getMaxSensitivityLevel() == null ? 0 : entity.getMaxSensitivityLevel())
                .scopes(entity.getScopes() == null ? Set.of() : Set.copyOf(entity.getScopes()))
                .toolAllowlist(entity.getToolAllowlist() == null ? List.of() : entity.getToolAllowlist())
                .tokenVersion(entity.getTokenVersion() == null ? 0L : entity.getTokenVersion())
                .status(AgentStatus.valueOf(entity.getStatus()))
                .rowVersion(entity.getRowVersion() == null ? 0L : entity.getRowVersion())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
