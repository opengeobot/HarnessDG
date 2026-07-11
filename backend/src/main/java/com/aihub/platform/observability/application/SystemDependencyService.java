/*
 * 功能: 系统依赖摘要应用服务，聚合探测结果并在依赖 DOWN 时触发告警与通知。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.platform.observability.application;

import com.aihub.notification.application.NotificationRecipientResolver;
import com.aihub.notification.application.NotificationService;
import com.aihub.notification.domain.NotificationSeverity;
import com.aihub.platform.observability.domain.DependencyHealth;
import com.aihub.platform.observability.domain.DependencyHealthProbe;
import com.aihub.platform.observability.domain.DependencyStatus;
import com.aihub.platform.observability.domain.SystemDependencySummary;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 系统依赖摘要应用服务。
 *
 * <p>聚合所有已注册的 {@link DependencyHealthProbe}，输出 {@link SystemDependencySummary}。
 * 定时监测依赖状态变化，DOWN 时触发 {@link AlertService} 与站内通知 fan-out。
 */
@Service
public class SystemDependencyService {

    private static final Logger LOG = LoggerFactory.getLogger(SystemDependencyService.class);

    private final List<DependencyHealthProbe> probes;
    private final AlertService alertService;
    private final NotificationService notificationService;
    private final NotificationRecipientResolver recipientResolver;
    private final Clock clock;
    private final Map<String, DependencyHealth> lastKnownHealth = new ConcurrentHashMap<>();

    public SystemDependencyService(List<DependencyHealthProbe> probes,
                                   AlertService alertService,
                                   NotificationService notificationService,
                                   NotificationRecipientResolver recipientResolver,
                                   Clock clock) {
        this.probes = probes;
        this.alertService = alertService;
        this.notificationService = notificationService;
        this.recipientResolver = recipientResolver;
        this.clock = clock;
    }

    /**
     * 探测全部依赖并聚合摘要。结果按依赖名称排序，保证响应稳定。
     *
     * @return 系统依赖与健康摘要
     */
    public SystemDependencySummary summarize() {
        List<DependencyStatus> statuses = new ArrayList<>(probes.size());
        for (DependencyHealthProbe probe : probes) {
            statuses.add(probe.probe());
        }
        statuses.sort(Comparator.comparing(DependencyStatus::name));
        return SystemDependencySummary.aggregate(statuses);
    }

    /** 定时监测依赖健康，DOWN 时告警并通知 system:observe 主体。 */
    @Scheduled(fixedDelayString = "${aihub.alert.check-interval:300000}")
    public void monitorDependencies() {
        try {
            SystemDependencySummary summary = summarize();
            for (DependencyStatus status : summary.dependencies()) {
                DependencyHealth previous = lastKnownHealth.put(status.name(), status.status());
                if (status.status() == DependencyHealth.DOWN) {
                    if (previous != DependencyHealth.DOWN) {
                        onDependencyDown(status);
                    }
                } else if (previous == DependencyHealth.DOWN) {
                    alertService.resolveDependencyUnhealthy(status.name());
                }
            }
        } catch (Exception ex) {
            LOG.warn("dependency monitoring failed", ex);
        }
    }

    private void onDependencyDown(DependencyStatus status) {
        String dependency = status.name();
        String healthStatus = status.status() == DependencyHealth.DEGRADED ? "DEGRADED" : "DOWN";
        alertService.emitDependencyUnhealthy(dependency, healthStatus, status.latencyMs());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dependency", dependency);
        payload.put("status", healthStatus);
        payload.put("latencyMs", status.latencyMs());
        payload.put("detectedAt", Instant.now(clock).toString());

        try {
            notificationService.publishOutboxEvent("SYSTEM_DEPENDENCY", dependency,
                    "SYSTEM_DEPENDENCY_UNHEALTHY", payload, Map.of());
        } catch (Exception ex) {
            LOG.warn("failed to publish SYSTEM_DEPENDENCY_UNHEALTHY outbox dependency={}", dependency, ex);
        }

        Set<String> observers = recipientResolver.resolveSystemObservers();
        notificationService.fanOutInAppNotifications(observers, null,
                "SYSTEM_DEPENDENCY_UNHEALTHY",
                "notification.system.dependency.unhealthy",
                NotificationSeverity.ERROR, payload);
        LOG.warn("dependency unhealthy dependency={} status={}", dependency, healthStatus);
    }
}
