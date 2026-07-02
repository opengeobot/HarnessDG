/*
 * 功能: 指标摘要服务单元测试——验证 JVM/任务/依赖指标聚合与空端口兜底。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.platform.observability.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aihub.platform.observability.domain.DependencyHealth;
import com.aihub.platform.observability.domain.DependencyHealthProbe;
import com.aihub.platform.observability.domain.DependencyStatus;
import com.aihub.platform.observability.domain.JobMetricsPort;
import com.aihub.platform.observability.domain.MetricsSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * {@link MetricsSummaryService} 单元测试。
 */
class MetricsSummaryServiceTest {

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    @Test
    void shouldAggregateJobCountsAndDependencies() {
        JobMetricsPort jobMetrics = status -> {
            if ("PENDING".equals(status)) return 5L;
            if ("RUNNING".equals(status)) return 2L;
            if ("DEAD".equals(status)) return 1L;
            return 0L;
        };
        SystemDependencyService dependencyService = new SystemDependencyService(List.of());
        Clock clock = Clock.fixed(Instant.parse("2026-07-01T10:00:00Z"), ZoneOffset.UTC);

        MetricsSummaryService service = new MetricsSummaryService(
                provider(jobMetrics), dependencyService,
                provider(new SimpleMeterRegistry()), clock);

        MetricsSummary summary = service.summarize();

        assertThat(summary.generatedAt()).isEqualTo(Instant.parse("2026-07-01T10:00:00Z"));
        assertThat(summary.jobs().get("pending")).isEqualTo(5L);
        assertThat(summary.jobs().get("running")).isEqualTo(2L);
        assertThat(summary.jobs().get("dead")).isEqualTo(1L);
        assertThat(summary.dependencies().get("overall")).isEqualTo("UP");
    }

    @Test
    void shouldFallbackToZeroWhenJobMetricsPortMissing() {
        SystemDependencyService dependencyService = new SystemDependencyService(List.of());
        Clock clock = Clock.fixed(Instant.parse("2026-07-01T10:00:00Z"), ZoneOffset.UTC);

        MetricsSummaryService service = new MetricsSummaryService(
                provider(null), dependencyService,
                provider(new SimpleMeterRegistry()), clock);

        MetricsSummary summary = service.summarize();

        assertThat(summary.jobs().get("pending")).isEqualTo(0L);
        assertThat(summary.jobs().get("dead")).isEqualTo(0L);
    }

    @Test
    void shouldIncludeDependencyHealthStatus() {
        SystemDependencyService dependencyService = new SystemDependencyService(List.of(
                new DependencyHealthProbe() {
                    @Override
                    public String name() {
                        return "postgres";
                    }

                    @Override
                    public DependencyStatus probe() {
                        return new DependencyStatus("postgres", DependencyHealth.UP, 12L);
                    }
                }));
        Clock clock = Clock.fixed(Instant.parse("2026-07-01T10:00:00Z"), ZoneOffset.UTC);

        MetricsSummaryService service = new MetricsSummaryService(
                provider(null), dependencyService,
                provider(new SimpleMeterRegistry()), clock);

        MetricsSummary summary = service.summarize();

        assertThat(summary.dependencies().get("overall")).isEqualTo("UP");
        assertThat(summary.dependencies().get("postgres")).isNotNull();
    }
}
