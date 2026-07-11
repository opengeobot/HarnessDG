/*
 * 功能: 按主体 + 工具名的令牌桶限流器，供 Agent REST 与 MCP tools/call 复用。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.platform.security;

import org.springframework.stereotype.Component;

/**
 * 主体级 + 工具级限流器。
 *
 * <p>每个 {@code principalId:toolName} 维护独立桶；补充速率由
 * {@link AgentRateLimitProperties#getRequestsPerMinute()} 决定，突发容量由
 * {@link AgentRateLimitProperties#getBurst()} 决定。存储后端由 {@code aihub.rate-limit.backend}
 * 选择（默认 memory，多实例可切换 redis）。
 */
@Component
public class RateLimiter {

    private final RateLimitBackend backend;
    private final double refillPerSecond;
    private final int burst;

    public RateLimiter(AgentRateLimitProperties properties, RateLimitBackend backend) {
        this.backend = backend;
        this.refillPerSecond = Math.max(properties.getRequestsPerMinute(), 1) / 60.0;
        this.burst = Math.max(properties.getBurst(), 1);
    }

    /**
     * 尝试获取一次调用配额。
     *
     * @param principalId 主体 ID
     * @param toolName    MCP 工具名或 REST 等价工具名
     * @return 是否允许本次调用
     */
    public boolean tryAcquire(String principalId, String toolName) {
        if (principalId == null || principalId.isBlank() || toolName == null || toolName.isBlank()) {
            return true;
        }
        String key = principalId + ":" + toolName;
        return backend.tryAcquire(key, refillPerSecond, burst);
    }
}
