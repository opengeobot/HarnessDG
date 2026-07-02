/*
 * 功能: Agent 工具授权仓储端口，约定以 iam_agent_tool 关联表为权威的工具白名单校验。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.authorization.domain;

/**
 * Agent 工具授权仓储端口。
 *
 * <p>以 {@code iam_agent_tool} 关联表为 Agent MCP Tool 授权的权威来源；领域层只依赖本端口。
 */
public interface AgentToolRepository {

    /**
     * 判断 Agent 是否被授权调用某 MCP 工具（启用态）。
     *
     * @param agentId  Agent 业务 ID
     * @param toolCode 工具编码
     * @return 是否允许
     */
    boolean isToolAllowed(String agentId, String toolCode);
}
