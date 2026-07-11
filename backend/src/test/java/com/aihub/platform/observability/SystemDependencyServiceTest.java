/*
 * 功能: SystemDependencyService 单元测试——验证依赖聚合与 DOWN 告警 fan-out。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.platform.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aihub.notification.application.NotificationRecipientResolver;
import com.aihub.notification.application.NotificationService;
import com.aihub.platform.observability.application.AlertService;
import com.aihub.platform.observability.application.SystemDependencyService;
import com.aihub.platform.observability.domain.DependencyHealth;
import com.aihub.platform.observability.domain.DependencyHealthProbe;
import com.aihub.platform.observability.domain.DependencyStatus;
import com.aihub.platform.observability.domain.SystemDependencySummary;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link SystemDependencyService} 离线单元测试。
 */
class SystemDependencyServiceTest {

    private AlertService alertService;
    private NotificationService notificationService;
    private NotificationRecipientResolver recipientResolver;
    private SystemDependencyService service;

    @BeforeEach
    void setUp() {
        alertService = mock(AlertService.class);
        notificationService = mock(NotificationService.class);
        recipientResolver = mock(NotificationRecipientResolver.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC);
        service = new SystemDependencyService(List.of(), alertService, notificationService,
                recipientResolver, clock);
    }

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
        service = new SystemDependencyService(List.of(
                stub("postgres", DependencyHealth.UP),
                stub("gitea", DependencyHealth.UP)),
                alertService, notificationService, recipientResolver,
                Clock.systemUTC());

        SystemDependencySummary summary = service.summarize();

        assertThat(summary.status()).isEqualTo(DependencyHealth.UP);
        assertThat(summary.dependencies()).hasSize(2);
    }

    @Test
    void overallIsDownWhenAnyDependencyDown() {
        service = new SystemDependencyService(List.of(
                stub("postgres", DependencyHealth.UP),
                stub("minio", DependencyHealth.DEGRADED),
                stub("gitea", DependencyHealth.DOWN)),
                alertService, notificationService, recipientResolver,
                Clock.systemUTC());

        SystemDependencySummary summary = service.summarize();

        assertThat(summary.status()).isEqualTo(DependencyHealth.DOWN);
        assertThat(summary.dependencies().stream().map(DependencyStatus::name))
                .containsExactly("gitea", "minio", "postgres");
    }

    @Test
    void monitorDependenciesFiresAlertOnDownTransition() {
        service = new SystemDependencyService(List.of(
                stub("gitea", DependencyHealth.DOWN)),
                alertService, notificationService, recipientResolver,
                Clock.systemUTC());
        when(recipientResolver.resolveSystemObservers()).thenReturn(Set.of("usr_observer"));

        service.monitorDependencies();

        verify(alertService).emitDependencyUnhealthy(eq("gitea"), any(), eq(1L));
        verify(notificationService).publishOutboxEvent(eq("SYSTEM_DEPENDENCY"), eq("gitea"),
                eq("SYSTEM_DEPENDENCY_UNHEALTHY"), any(), any());
        verify(notificationService).fanOutInAppNotifications(eq(Set.of("usr_observer")), eq(null),
                eq("SYSTEM_DEPENDENCY_UNHEALTHY"), any(), any(), any());
    }
}
