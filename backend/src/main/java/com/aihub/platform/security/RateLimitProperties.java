/*
 * 功能: 分布式限流后端配置属性。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.platform.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 限流后端配置，绑定 {@code aihub.rate-limit.*}。
 */
@ConfigurationProperties(prefix = "aihub.rate-limit")
public class RateLimitProperties {

    /** 后端类型：memory（默认）或 redis。 */
    private String backend = "memory";

    /** Redis 主机（仅 backend=redis 时使用）。 */
    private String redisHost = "localhost";

    /** Redis 端口（仅 backend=redis 时使用）。 */
    private int redisPort = 6379;

    public String getBackend() {
        return backend;
    }

    public void setBackend(String backend) {
        this.backend = backend;
    }

    public String getRedisHost() {
        return redisHost;
    }

    public void setRedisHost(String redisHost) {
        this.redisHost = redisHost;
    }

    public int getRedisPort() {
        return redisPort;
    }

    public void setRedisPort(int redisPort) {
        this.redisPort = redisPort;
    }
}
