/*
 * 功能: Agent/MCP 主体级限流配置属性。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.platform.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent/MCP 限流配置，绑定 {@code aihub.agent.rate-limit.*}。
 */
@ConfigurationProperties(prefix = "aihub.agent.rate-limit")
public class AgentRateLimitProperties {

    /** 每分钟允许的请求数（令牌补充速率）。 */
    private int requestsPerMinute = 60;

    /** 突发容量（令牌桶上限）。 */
    private int burst = 10;

    public int getRequestsPerMinute() {
        return requestsPerMinute;
    }

    public void setRequestsPerMinute(int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }

    public int getBurst() {
        return burst;
    }

    public void setBurst(int burst) {
        this.burst = burst;
    }
}
