/*
 * 功能: Agent 视图，对应契约 AgentView；绝不包含凭据哈希。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.application;

import com.aihub.identity.domain.AgentIdentity;
import com.aihub.identity.domain.AgentStatus;
import java.util.List;

/**
 * Agent 视图。
 *
 * @param agentId             Agent ID（agt_）
 * @param principalId         主体 ID（prn_）
 * @param displayName         展示名称
 * @param agentType           Agent 类型
 * @param vendor              供应商
 * @param status              状态
 * @param maxSensitivityLevel 最高敏感等级
 * @param scopes              粗粒度 Scope
 * @param toolAllowlist       MCP Tool 白名单
 */
public record AgentView(String agentId,
                        String principalId,
                        String displayName,
                        String agentType,
                        String vendor,
                        AgentStatus status,
                        int maxSensitivityLevel,
                        List<String> scopes,
                        List<String> toolAllowlist) {

    /**
     * 由领域聚合构造视图（不暴露凭据哈希）。
     */
    public static AgentView from(AgentIdentity agent) {
        return new AgentView(
                agent.agentId(),
                agent.principalId(),
                agent.displayName(),
                agent.agentType(),
                agent.vendor(),
                agent.status(),
                agent.maxSensitivityLevel(),
                List.copyOf(agent.scopes()),
                List.copyOf(agent.toolAllowlist()));
    }
}
