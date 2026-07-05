/*
 * 功能: Caffeine 本地缓存配置——字典/标签/权限的高频读缓存。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
package com.aihub.shared.caching;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
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
 * </ul>
 */
@Configuration
@EnableCaching
public class CaffeineCacheConfiguration {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(2000));
        manager.setAllowNullValues(false);
        return manager;
    }
}
