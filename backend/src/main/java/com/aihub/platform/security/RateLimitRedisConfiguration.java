/*
 * 功能: Redis 限流后端连接装配。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.platform.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 限流连接工厂（仅 backend=redis 时装配）。
 */
@Configuration
@ConditionalOnProperty(name = "aihub.rate-limit.backend", havingValue = "redis")
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitRedisConfiguration {

    @Bean
    public LettuceConnectionFactory rateLimitRedisConnectionFactory(RateLimitProperties properties) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(properties.getRedisHost());
        config.setPort(properties.getRedisPort());
        return new LettuceConnectionFactory(config);
    }

    @Bean
    public StringRedisTemplate rateLimitStringRedisTemplate(LettuceConnectionFactory rateLimitRedisConnectionFactory) {
        return new StringRedisTemplate(rateLimitRedisConnectionFactory);
    }
}
