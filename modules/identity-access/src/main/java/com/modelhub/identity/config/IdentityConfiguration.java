package com.modelhub.identity.config;

import com.modelhub.identity.service.ratelimit.LocalRateLimiter;
import com.modelhub.identity.service.ratelimit.RateLimiter;
import com.modelhub.identity.service.ratelimit.RedisRateLimiter;
import com.modelhub.identity.service.ratelimit.ResilientRateLimiter;
import com.modelhub.shared.idempotency.JdbcIdempotencyService;
import com.modelhub.shared.metrics.BusinessCounters;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * identity-access 模块装配：BCrypt 密码哈希（02 §6.2）、限流降级组合、幂等服务。
 */
@Configuration
@EnableConfigurationProperties({IdentityProperties.class, BootstrapProperties.class})
public class IdentityConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public RateLimiter rateLimiter(StringRedisTemplate redis, IdentityProperties props,
                                   BusinessCounters counters) {
        return new ResilientRateLimiter(new RedisRateLimiter(redis, props), new LocalRateLimiter(props), counters);
    }

    @Bean
    public JdbcIdempotencyService idempotencyService(NamedParameterJdbcTemplate jdbc) {
        return new JdbcIdempotencyService(jdbc);
    }
}
