/*
 * 功能: Agent 身份仓储端口，约定 Agent 与其主体的持久化与查询能力。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.domain;

import java.util.List;
import java.util.Optional;

/**
 * Agent 身份仓储端口。
 */
public interface AgentIdentityRepository {

    /**
     * 创建主体与 Agent（同一事务）。
     */
    void create(PrincipalAccount principal, AgentIdentity agent);

    /**
     * 持久化 Agent 状态变更（基于 row_version 乐观锁）。
     *
     * @return 是否更新成功
     */
    boolean update(AgentIdentity agent);

    Optional<AgentIdentity> findByAgentId(String agentId);

    Optional<AgentIdentity> findByPrincipalId(String principalId);

    List<AgentIdentity> findAll();
}
