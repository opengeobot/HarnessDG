/*
 * 功能: AlertService 单元测试——DEAD Job / Webhook / Outbox 告警触发与恢复。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.platform.observability.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aihub.notification.domain.OutboxRepository;
import com.aihub.notification.domain.WebhookDeliveryRepository;
import com.aihub.notification.domain.WebhookDeliveryStatus;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlertService")
class AlertServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private OutboxRepository outboxRepository;
    @Mock
    private WebhookDeliveryRepository deliveryRepository;
    @Mock
    private IdGenerator idGenerator;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-10T12:00:00Z"), ZoneOffset.UTC);
    private AlertService alertService;

    @BeforeEach
    void setUp() {
        alertService = new AlertService(jdbcTemplate, outboxRepository, deliveryRepository, idGenerator, clock);
    }

    @Test
    @DisplayName("存在 DEAD Job 时应触发 DEAD_JOB 告警")
    void checkDeadJobsShouldFireWhenDeadJobsExist() {
        when(deliveryRepository.countByStatus(WebhookDeliveryStatus.DEAD)).thenReturn(0L);
        when(outboxRepository.countPending()).thenReturn(0L);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM job_task WHERE status = 'DEAD'"), eq(Long.class)))
                .thenReturn(2L);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM system_alert WHERE alert_type = ? AND status = 'FIRING'"),
                eq(Long.class), eq("DEAD_DELIVERY")))
                .thenReturn(0L);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM system_alert WHERE alert_type = ? AND status = 'FIRING'"),
                eq(Long.class), eq("OUTBOX_BACKLOG")))
                .thenReturn(0L);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM system_alert WHERE alert_type = ? AND status = 'FIRING'"),
                eq(Long.class), eq("DEAD_JOB")))
                .thenReturn(0L);
        when(idGenerator.generate(IdPrefix.ALERT)).thenReturn("alert_dead_job");

        alertService.checkAlerts();

        verify(jdbcTemplate).update(
                contains("INSERT INTO system_alert"),
                eq("alert_dead_job"),
                eq("DEAD_JOB"),
                eq("CRITICAL"),
                any(),
                any(),
                eq("aihub_job_dead_count"),
                any(),
                eq(2.0),
                any());
    }

    @Test
    @DisplayName("无 DEAD Job 时应恢复 DEAD_JOB 告警")
    void checkDeadJobsShouldResolveWhenNoDeadJobs() {
        when(deliveryRepository.countByStatus(WebhookDeliveryStatus.DEAD)).thenReturn(0L);
        when(outboxRepository.countPending()).thenReturn(0L);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM job_task WHERE status = 'DEAD'"), eq(Long.class)))
                .thenReturn(0L);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM system_alert WHERE alert_type = ? AND status = 'FIRING'"),
                eq(Long.class), eq("DEAD_DELIVERY")))
                .thenReturn(0L);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM system_alert WHERE alert_type = ? AND status = 'FIRING'"),
                eq(Long.class), eq("OUTBOX_BACKLOG")))
                .thenReturn(0L);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM system_alert WHERE alert_type = ? AND status = 'FIRING'"),
                eq(Long.class), eq("DEAD_JOB")))
                .thenReturn(1L);

        alertService.checkAlerts();

        verify(jdbcTemplate).update(
                contains("UPDATE system_alert SET status = 'RESOLVED'"),
                any(),
                eq(0.0),
                eq("DEAD_JOB"));
        verify(idGenerator, never()).generate(IdPrefix.ALERT);
    }
}
