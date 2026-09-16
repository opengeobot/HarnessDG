/*
 * 功能: 性能基准测试 Spring 配置——使用 Testcontainers PostgreSQL 提供性能测试专用数据源。
 *       通过 test.performance.enabled 属性控制，正常测试不受影响。
 * 时间: 2026-07-30
 * 作者: AxeXie
 */
package com.aihub.performance;

import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 性能基准测试专用配置。
 *
 * <p>仅在 {@code test.performance.enabled=true} 时激活。启动独立 PostgreSQL 容器执行 Flyway 迁移，
 * 供性能测试注入测试数据并执行高负载查询。无 Docker 时由 {@code @Testcontainers(disabledWithoutDocker = true)}
 * 自动跳过，不阻断 {@code ./mvnw verify}。
 */
@Testcontainers(disabledWithoutDocker = true)
@TestConfiguration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "test.performance.enabled", havingValue = "true")
public class PerformanceBenchmarkConfiguration {

    /** 性能测试专用 PostgreSQL 容器（隔离实例，避免污染功能测试）。 */
    @Container
    static final PostgreSQLContainer<?> PERFORMANCE_POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("aihub_perf")
                    .withUsername("aihub")
                    .withPassword("aihub")
                    .withStartupAttempts(3);

    /** 将 Testcontainers 数据源属性注册到 Spring 环境，供 Flyway 和 MyBatis-Plus 自动装配。 */
    @DynamicPropertySource
    static void registerPerformanceDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PERFORMANCE_POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", PERFORMANCE_POSTGRES::getUsername);
        registry.add("spring.datasource.password", PERFORMANCE_POSTGRES::getPassword);
    }

    @Bean
    JdbcTemplate performanceJdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}