/*
 * 功能: 限流后端端口——内存或 Redis 令牌桶/滑动窗口实现。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.platform.security;

/**
 * 限流存储后端。
 *
 * @param key              限流键（principalId:toolName）
 * @param refillPerSecond  令牌补充速率（每秒）
 * @param burst            突发容量
 * @return 是否允许本次调用
 */
public interface RateLimitBackend {

    boolean tryAcquire(String key, double refillPerSecond, int burst);
}
