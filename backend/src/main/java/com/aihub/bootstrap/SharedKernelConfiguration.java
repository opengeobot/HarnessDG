/*
 * 功能: 公共能力 Bean 装配，向上下文注册 shared-kernel 与 platform 的无状态组件。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.bootstrap;

import com.aihub.platform.security.BCryptPasswordHasher;
import com.aihub.platform.security.JwtProperties;
import com.aihub.platform.security.JwtTokenService;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.UlidIdGenerator;
import com.aihub.shared.logging.SensitiveDataMasker;
import com.aihub.shared.security.PasswordHasher;
import com.aihub.shared.security.PasswordPolicy;
import com.aihub.shared.security.TokenSigner;
import com.aihub.shared.security.TokenVerifier;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 公共能力 Bean 装配。
 *
 * <p>shared-kernel 自身不依赖 Spring，由 bootstrap 负责把其无状态实现与 platform 实现注册为 Bean。
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SharedKernelConfiguration {

    /**
     * @return 基于 ULID 的业务 ID 生成器
     */
    @Bean
    public IdGenerator idGenerator() {
        return new UlidIdGenerator();
    }

    /**
     * @return 系统时钟（便于测试替换）
     */
    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }

    /**
     * @return 基于 JJWT 的 JWT 签发/校验服务（同时实现 {@link TokenSigner} 与 {@link TokenVerifier}）
     */
    @Bean
    public JwtTokenService jwtTokenService(JwtProperties jwtProperties, IdGenerator idGenerator, Clock systemClock) {
        return new JwtTokenService(jwtProperties, idGenerator, systemClock);
    }

    /**
     * @return Token 签发器（复用 {@link JwtTokenService}）
     */
    @Bean
    public TokenSigner tokenSigner(JwtTokenService jwtTokenService) {
        return jwtTokenService;
    }

    /**
     * @return Token 校验器（复用 {@link JwtTokenService}）
     */
    @Bean
    public TokenVerifier tokenVerifier(JwtTokenService jwtTokenService) {
        return jwtTokenService;
    }

    /**
     * @return 基于 BCrypt 的口令哈希器
     */
    @Bean
    public PasswordHasher passwordHasher() {
        return new BCryptPasswordHasher();
    }

    /**
     * @return 平台默认口令强度策略
     */
    @Bean
    public PasswordPolicy passwordPolicy() {
        return PasswordPolicy.defaults();
    }

    /**
     * @return 字段级敏感数据脱敏器
     */
    @Bean
    public SensitiveDataMasker sensitiveDataMasker() {
        return new SensitiveDataMasker();
    }
}
