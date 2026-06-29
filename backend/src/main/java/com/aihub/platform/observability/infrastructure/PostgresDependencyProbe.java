/*
 * 功能: PostgreSQL 依赖健康探测适配器，通过 DataSource 验证连通性并计时。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.platform.observability.infrastructure;

import com.aihub.platform.observability.domain.DependencyHealth;
import com.aihub.platform.observability.domain.DependencyHealthProbe;
import com.aihub.platform.observability.domain.DependencyStatus;
import java.sql.Connection;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/**
 * PostgreSQL 依赖健康探测适配器。
 *
 * <p>通过 {@link DataSource} 建立连接并做有界超时校验，作为系统诊断 API 的依赖探测来源。
 * 与 Actuator readiness 探针（{@code PostgresHealthIndicator}）职责不同：前者服务于容器探针摘除流量，
 * 此处服务于运维诊断接口，二者独立避免反向依赖启动装配层。
 */
@Component
public class PostgresDependencyProbe implements DependencyHealthProbe {

    private static final String DEPENDENCY_NAME = "postgres";
    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;

    public PostgresDependencyProbe(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String name() {
        return DEPENDENCY_NAME;
    }

    @Override
    public DependencyStatus probe() {
        long startNanos = System.nanoTime();
        try (Connection connection = dataSource.getConnection()) {
            boolean valid = connection.isValid(VALIDATION_TIMEOUT_SECONDS);
            long latencyMs = elapsedMillis(startNanos);
            DependencyHealth health = valid ? DependencyHealth.UP : DependencyHealth.DOWN;
            return new DependencyStatus(DEPENDENCY_NAME, health, latencyMs);
        } catch (Exception ex) {
            return new DependencyStatus(DEPENDENCY_NAME, DependencyHealth.DOWN, elapsedMillis(startNanos));
        }
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
