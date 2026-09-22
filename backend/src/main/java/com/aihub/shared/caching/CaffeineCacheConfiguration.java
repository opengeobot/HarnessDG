/*
 * 功能: Caffeine 本地缓存配置——字典/标签/权限的高频读缓存。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
package com.aihub.shared.caching;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Caffeine 本地缓存配置。
 *
 * <p>为高频读、低频写的治理数据（字典、标签、权限）提供本地缓存，降低数据库压力。
 * 缓存策略：
 * <ul>
 *   <li>{@code dictionaryCache}：5 分钟过期，最大 2000 条</li>
 *   <li>{@code tagCache}：5 分钟过期，最大 500 条</li>
 *   <li>{@code permissionCache}：30 秒过期，最大 1000 条</li>
 *   <li>{@code facetCache}：60 秒过期，最大 500 条（Facet 聚合查询）</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CaffeineCacheConfiguration {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(java.util.List.of(
                buildCache("dictionaryCache", 5, 2000),
                buildCache("tagCache", 5, 500),
                buildCache("permissionCache", 30, 1000)
        ));
        return manager;
    }

    /** Facet 聚合查询缓存——key 为过滤参数 hash，TTL 60s。 */
    @Bean
    public Cache<String, Map<String, Map<String, Long>>> facetCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .maximumSize(500)
                .build();
    }

    private static CaffeineCache buildCache(String name, int ttlMinutes, int maxSize) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
                .maximumSize(maxSize)
                .build());
    }
}
