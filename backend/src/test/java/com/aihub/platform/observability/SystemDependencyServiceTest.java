/*
 * 功能: SystemDependencyService 单元测试——验证依赖聚合与总体状态计算。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.platform.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.aihub.platform.observability.application.SystemDependencyService;
import com.aihub.platform.observability.domain.DependencyHealth;
import com.aihub.platform.observability.domain.DependencyHealthProbe;
import com.aihub.platform.observability.domain.DependencyStatus;
import com.aihub.platform.observability.domain.SystemDependencySummary;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link SystemDependencyService} 离线单元测试，使用桩探测验证聚合逻辑。
 */
class SystemDependencyServiceTest {

    private static DependencyHealthProbe stub(String name, DependencyHealth health) {
        return new DependencyHealthProbe() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public DependencyStatus probe() {
                return new DependencyStatus(name, health, 1L);
            }
        };
    }

    @Test
    void overallIsUpWhenAllDependenciesUp() {
        SystemDependencyService service = new SystemDependencyService(List.of(
                stub("postgres", DependencyHealth.UP),
                stub("gitea", DependencyHealth.UP)));

        SystemDependencySummary summary = service.summarize();

        assertThat(summary.status()).isEqualTo(DependencyHealth.UP);
        assertThat(summary.dependencies()).hasSize(2);
    }

    @Test
    void overallIsDownWhenAnyDependencyDown() {
        SystemDependencyService service = new SystemDependencyService(List.of(
                stub("postgres", DependencyHealth.UP),
                stub("minio", DependencyHealth.DEGRADED),
                stub("gitea", DependencyHealth.DOWN)));

        SystemDependencySummary summary = service.summarize();

        assertThat(summary.status()).isEqualTo(DependencyHealth.DOWN);
        // 结果按名称排序，保证响应稳定。
        assertThat(summary.dependencies().stream().map(DependencyStatus::name))
                .containsExactly("gitea", "minio", "postgres");
    }

    @Test
    void overallIsUpWhenNoProbesRegistered() {
        SystemDependencyService service = new SystemDependencyService(List.of());

        SystemDependencySummary summary = service.summarize();

        assertThat(summary.status()).isEqualTo(DependencyHealth.UP);
        assertThat(summary.dependencies()).isEmpty();
    }
}
