/*
 * 功能: Spring 上下文加载冒烟集成测试——用 Testcontainers PostgreSQL 验证 Flyway 迁移与上下文装配。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 上下文加载冒烟测试。
 *
 * <p>用 Testcontainers 启动真实 PostgreSQL，验证 Flyway 基线迁移成功执行且 Spring 上下文正常装配。
 * 当本机无 Docker 时（如离线 CI），{@code disabledWithoutDocker = true} 使本类整体跳过，
 * 保证 {@code ./mvnw verify} 在无 Docker 网络时仍可通过。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextSmokeIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("aihub")
                    .withUsername("aihub")
                    .withPassword("aihub");

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoadsAndBaselineMigrationApplied() {
        assertThat(dataSource).isNotNull();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        Integer baselineRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM platform_schema_info WHERE schema_key = 'baseline'", Integer.class);
        assertThat(baselineRows).isEqualTo(1);

        Integer flywayApplied = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        assertThat(flywayApplied).isGreaterThanOrEqualTo(1);
    }
}
