/*
 * 功能: 指标摘要应用服务，聚合 JVM/任务/依赖等运维指标生成受限摘要。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.platform.observability.application;

import com.aihub.platform.observability.domain.DependencyHealth;
import com.aihub.platform.observability.domain.DependencyStatus;
import com.aihub.platform.observability.domain.JobMetricsPort;
import com.aihub.platform.observability.domain.MetricsSummary;
import com.aihub.platform.observability.domain.SystemDependencySummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 指标摘要应用服务。
 *
 * <p>聚合 JVM 堆/线程、任务状态计数、依赖健康与 API 请求指标，生成 {@link MetricsSummary}。
 * 仅暴露非敏感运维指标，不包含凭据/内部 Endpoint/网络拓扑。各数据源以 ObjectProvider 注入，
 * 缺失时以安全默认值兜底（如仅 REST 角色实例无 JobMetricsPort 时任务计数为 0）。
 */
@Service
public class MetricsSummaryService {

    private static final String[] JOB_STATUSES = {"PENDING", "RUNNING", "DEAD", "SUCCEEDED", "CANCELLED", "RETRY_WAIT"};
    private static final String HTTP_SERVER_REQUESTS = "http.server.requests";

    private final ObjectProvider<JobMetricsPort> jobMetricsPortProvider;
    private final SystemDependencyService systemDependencyService;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;
    private final Clock clock;

    public MetricsSummaryService(ObjectProvider<JobMetricsPort> jobMetricsPortProvider,
                                 SystemDependencyService systemDependencyService,
                                 ObjectProvider<MeterRegistry> meterRegistryProvider,
                                 Clock clock) {
        this.jobMetricsPortProvider = jobMetricsPortProvider;
        this.systemDependencyService = systemDependencyService;
        this.meterRegistryProvider = meterRegistryProvider;
        this.clock = clock;
    }

    /**
     * 生成平台指标摘要。
     *
     * @return 包含 JVM/任务/依赖/API 指标的摘要视图
     */
    public MetricsSummary summarize() {
        Instant generatedAt = clock.instant();
        return new MetricsSummary(generatedAt, buildApiMetrics(), buildJobMetrics(), buildDependencyMetrics());
    }

    private Map<String, Object> buildApiMetrics() {
        Map<String, Object> api = new LinkedHashMap<>();
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            registry.find(HTTP_SERVER_REQUESTS).timers().forEach(timer ->
                    api.put(safeName(timer), extractTimerSnapshot(timer)));
        }
        api.put("jvm", buildJvmMetrics());
        return api;
    }

    private Map<String, Object> buildJvmMetrics() {
        Map<String, Object> jvm = new LinkedHashMap<>();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        jvm.put("heapUsedBytes", memory.getHeapMemoryUsage().getUsed());
        jvm.put("heapMaxBytes", memory.getHeapMemoryUsage().getMax());
        jvm.put("threadCount", threads.getThreadCount());
        return jvm;
    }

    private static String safeName(Timer timer) {
        String name = timer.getId().getName();
        return name == null ? HTTP_SERVER_REQUESTS : name;
    }

    private static Map<String, Object> extractTimerSnapshot(Timer timer) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("count", timer.count());
        snapshot.put("totalTimeMs", (long) timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS));
        return snapshot;
    }

    private Map<String, Object> buildJobMetrics() {
        Map<String, Object> jobs = new LinkedHashMap<>();
        JobMetricsPort port = jobMetricsPortProvider.getIfAvailable();
        for (String status : JOB_STATUSES) {
            long count = port != null ? port.countByStatus(status) : 0;
            jobs.put(status.toLowerCase(), count);
        }
        return jobs;
    }

    private Map<String, Object> buildDependencyMetrics() {
        SystemDependencySummary summary = systemDependencyService.summarize();
        Map<String, Object> deps = new LinkedHashMap<>();
        DependencyHealth overall = summary.status();
        deps.put("overall", overall.name());
        List<DependencyStatus> statuses = summary.dependencies();
        for (DependencyStatus status : statuses) {
            deps.put(status.name(), Map.of(
                    "status", status.status().name(),
                    "latencyMs", status.latencyMs() != null ? status.latencyMs() : -1));
        }
        return deps;
    }
}
