/*
 * 功能: Agent 工具授权仓储 JDBC 实现，以 iam_agent_tool 关联表为权威校验 Agent 的 MCP Tool 白名单。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.authorization.infrastructure;

import com.aihub.authorization.domain.AgentToolRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Agent 工具授权仓储 JDBC 实现。
 */
@Repository
public class JdbcAgentToolRepository implements AgentToolRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcAgentToolRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean isToolAllowed(String agentId, String toolCode) {
        if (agentId == null || toolCode == null) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(1) FROM iam_agent_tool WHERE agent_id = :agentId "
                        + "AND tool_code = :toolCode AND enabled = 1",
                new MapSqlParameterSource()
                        .addValue("agentId", agentId)
                        .addValue("toolCode", toolCode), Integer.class);
        return count != null && count > 0;
    }
}
