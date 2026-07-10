/*
 * 功能: WebhookInboxHandler 单元测试。覆盖：事件分发、Tag 删除检测、幂等处理、空 payload 防御。
 * 时间: 2026-07-04
 */
package com.aihub.integration.gitea.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aihub.job.domain.JobContext;
import com.aihub.platform.observability.application.PlatformMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class WebhookInboxHandlerTest {

    private JdbcTemplate jdbcTemplate;
    private ObjectMapper objectMapper;
    private PlatformMetrics platformMetrics;
    private WebhookInboxHandler handler;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        objectMapper = new ObjectMapper();
        platformMetrics = mock(PlatformMetrics.class);
        handler = new WebhookInboxHandler(jdbcTemplate, objectMapper, platformMetrics);
    }

    @Test
    void typeReturnsWebhookProcess() {
        assertThat(handler.type()).isEqualTo("WEBHOOK_PROCESS");
    }

    @Test
    void handleWithEmptyPayloadSweepsPendingInbox() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), anyInt())).thenReturn(List.of());

        JobContext ctx = new JobContext("job-1", "WEBHOOK_PROCESS", "", 0, "usr_1", "trace-1", null);
        handler.handle(ctx);

        verify(jdbcTemplate).queryForList(anyString(), eq(String.class), anyInt());
        verify(jdbcTemplate, never()).queryForMap(anyString());
    }

    @Test
    void handleWithNullPayloadSweepsPendingInbox() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), anyInt())).thenReturn(List.of());

        JobContext ctx = new JobContext("job-1", "WEBHOOK_PROCESS", null, 0, "usr_1", "trace-1", null);
        handler.handle(ctx);

        verify(jdbcTemplate).queryForList(anyString(), eq(String.class), anyInt());
    }

    @Test
    void handleWithMissingDeliveryIdDoesNotQuery() {
        JobContext ctx = new JobContext("job-1", "WEBHOOK_PROCESS", "{}", 0, "usr_1", "trace-1", null);
        handler.handle(ctx);
        verify(jdbcTemplate, never()).queryForMap(anyString());
    }

    @Test
    void handleSkipsAlreadyCompletedEvent() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 1);
        row.put("event_type", "push");
        row.put("payload", "{}");
        row.put("status", "COMPLETED");

        when(jdbcTemplate.queryForMap(anyString(), anyString())).thenReturn(row);

        String payload = "{\"deliveryId\":\"dlv-1\"}";
        JobContext ctx = new JobContext("job-1", "WEBHOOK_PROCESS", payload, 0, "usr_1", "trace-1", null);
        handler.handle(ctx);

        // Should NOT update status to PROCESSING (idempotent)
        verify(jdbcTemplate, never()).update(eq(
                "UPDATE webhook_inbox SET status = 'PROCESSING' WHERE delivery_id = ? AND status = 'PENDING'"),
                anyString());
    }

    @Test
    void handleProcessesPushEvent() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 1);
        row.put("event_type", "push");
        row.put("payload", "{\"repository\":{\"full_name\":\"org/repo\"},\"ref\":\"refs/heads/main\",\"forced\":false}");
        row.put("status", "PENDING");

        when(jdbcTemplate.queryForMap(anyString(), anyString())).thenReturn(row);
        when(jdbcTemplate.update(anyString(), anyString())).thenReturn(1);

        String payload = "{\"deliveryId\":\"dlv-2\"}";
        JobContext ctx = new JobContext("job-2", "WEBHOOK_PROCESS", payload, 0, "usr_1", "trace-2", null);
        handler.handle(ctx);

        // Should update PROCESSING then COMPLETED
        verify(jdbcTemplate, times(2)).update(anyString(), anyString());
    }

    @Test
    void handleDetectsTagDeleteEvent() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 2);
        row.put("event_type", "delete");
        row.put("payload", "{\"ref_type\":\"tag\",\"ref\":\"v1.0.0\",\"repository\":{\"full_name\":\"org/model-asset\"}}");
        row.put("status", "PENDING");

        when(jdbcTemplate.queryForMap(anyString(), anyString())).thenReturn(row);
        when(jdbcTemplate.update(anyString(), anyString())).thenReturn(1);

        String payload = "{\"deliveryId\":\"dlv-3\"}";
        JobContext ctx = new JobContext("job-3", "WEBHOOK_PROCESS", payload, 0, "usr_1", "trace-3", null);
        // Should NOT throw — tag deletion detected, not error
        handler.handle(ctx);

        // Should mark as PROCESSING then COMPLETED
        verify(jdbcTemplate, times(2)).update(anyString(), anyString());
    }

    @Test
    void handleSweepsPendingInboxWhenPayloadEmpty() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), anyInt()))
                .thenReturn(List.of("dlv-sweep-1"));
        Map<String, Object> row = new HashMap<>();
        row.put("id", 1);
        row.put("event_type", "push");
        row.put("payload", "{\"repository\":{\"full_name\":\"org/repo\"},\"ref\":\"refs/heads/main\"}");
        row.put("status", "PENDING");
        when(jdbcTemplate.queryForMap(anyString(), anyString())).thenReturn(row);
        when(jdbcTemplate.update(anyString(), anyString())).thenReturn(1);

        JobContext ctx = new JobContext("job-sweep", "WEBHOOK_PROCESS", "{}", 0, "usr_1", "trace-1", null);
        handler.handle(ctx);

        verify(jdbcTemplate).queryForList(anyString(), eq(String.class), anyInt());
        verify(jdbcTemplate, times(2)).update(anyString(), anyString());
    }

    @Test
    void handleThrowsOnDatabaseFailure() {
        when(jdbcTemplate.queryForMap(anyString(), anyString())).thenThrow(new RuntimeException("DB down"));

        String payload = "{\"deliveryId\":\"dlv-4\"}";
        JobContext ctx = new JobContext("job-4", "WEBHOOK_PROCESS", payload, 0, "usr_1", "trace-4", null);

        assertThatThrownBy(() -> handler.handle(ctx))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("webhook processing failed");
    }
}
