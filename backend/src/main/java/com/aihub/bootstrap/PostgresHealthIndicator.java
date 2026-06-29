/*
 * 功能: Readiness 健康指示器，通过 DataSource 探测 PostgreSQL 可用性。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.bootstrap;

import javax.sql.DataSource;
import java.sql.Connection;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * PostgreSQL Readiness 健康指示器。
 *
 * <p>注册为名为 {@code db} 的健康贡献者并纳入 readiness 分组：无法建立连接或连接校验失败时返回 DOWN，
 * 使实例从流量中摘除，但不影响 liveness（容器不会因依赖抖动被误重启）。探测使用短超时避免拖垮探针。
 * 通过 {@code management.health.db.enabled=false} 关闭默认 DataSource 健康检查以本实现替代。
 */
@Component("db")
public class PostgresHealthIndicator implements HealthIndicator {

    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;

    public PostgresHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(VALIDATION_TIMEOUT_SECONDS)) {
                return Health.up().withDetail("database", connection.getCatalog()).build();
            }
            return Health.down().withDetail("reason", "connection validation failed").build();
        } catch (Exception ex) {
            return Health.down().withDetail("reason", "datasource unavailable").build();
        }
    }
}
