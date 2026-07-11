/*
 * 功能: Redis 限流计数端口——便于单测注入内存实现。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.platform.security;

/**
 * Redis 滑动窗口计数操作。
 */
public interface RedisRateLimitCounter {

    /**
     * 递增计数并在首次写入时设置 TTL。
     *
     * @param key         Redis 键
     * @param ttlSeconds  过期秒数
     * @return 递增后的计数值
     */
    long increment(String key, int ttlSeconds);
}
