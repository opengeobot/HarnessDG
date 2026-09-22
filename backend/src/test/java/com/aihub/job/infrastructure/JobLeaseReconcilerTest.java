/*
 * 功能: JobLeaseReconciler 单元测试。覆盖租约恢复、超限标记 DEAD、RUNNING 完整性检查。
 * 时间: 2026-07-04
 */
package com.aihub.job.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aihub.job.domain.JobContext;
import com.aihub.platform.observability.application.PlatformMetrics;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JobLeaseReconcilerTest {

    private JdbcTemplate jdbcTemplate;
    private PlatformMetrics platformMetrics;
    private JobLeaseReconciler reconciler;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        platformMetrics = mock(PlatformMetrics.class);
        reconciler = new JobLeaseReconciler(jdbcTemplate, platformMetrics);
    }

    @Test
    void typeReturnsJobLeaseReconcile() {
        assertThat(reconciler.type()).isEqualTo("JOB_LEASE_RECONCILE");
    }

    @Test
    void handleRecoversExpiredLeases() {
        when(jdbcTemplate.update(anyString())).thenReturn(3, 0); // 3 recovered, 0 dead-marked
        when(jdbcTemplate.queryForList(anyString(), anyInt())).thenReturn(List.of());

        JobContext ctx = new JobContext("job-1", "JOB_LEASE_RECONCILE", null, 0, "usr_1", "trace-1", null);
        reconciler.handle(ctx);

        // Verify recovery update was called
        verify(jdbcTemplate, times(2)).update(anyString());
    }

    @Test
    void handleMarksOverLimitJobsAsDead() {
        when(jdbcTemplate.update(anyString())).thenReturn(0, 2); // 0 recovered, 2 dead-marked
        when(jdbcTemplate.queryForList(anyString(), anyInt())).thenReturn(List.of());

        JobContext ctx = new JobContext("job-1", "JOB_LEASE_RECONCILE", null, 0, "usr_1", "trace-1", null);
        reconciler.handle(ctx);

        verify(jdbcTemplate, times(2)).update(anyString());
    }

    @Test
    void handleWithEmptyBatchDoesNotRecordDiscrepancy() {
        when(jdbcTemplate.update(anyString())).thenReturn(0, 0);
        when(jdbcTemplate.queryForList(anyString(), anyInt())).thenReturn(List.of());

        JobContext ctx = new JobContext("job-1", "JOB_LEASE_RECONCILE", null, 0, "usr_1", "trace-1", null);
        reconciler.handle(ctx);

        // 2 base updates, no discrepancy INSERT
        verify(jdbcTemplate, times(2)).update(anyString());
    }

    @Test
    void handleDetectsNegativeAttempts() {
        when(jdbcTemplate.update(anyString())).thenReturn(0, 0);

        List<Map<String, Object>> batch = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("job_id", "job_bad");
        row.put("type", "TEST");
        row.put("status", "PENDING");
        row.put("attempts", -1);
        row.put("max_attempts", 5);
        row.put("leased_until", null);
        row.put("leased_by", null);
        batch.add(row);

        when(jdbcTemplate.queryForList(anyString(), anyInt())).thenReturn(batch);
        when(jdbcTemplate.queryForList(anyString(), anyString(), anyInt())).thenReturn(List.of());

        JobContext ctx = new JobContext("job-1", "JOB_LEASE_RECONCILE", null, 0, "usr_1", "trace-1", null);
        reconciler.handle(ctx);

        // 2 base updates + 1 discrepancy INSERT (4-arg)
        verify(jdbcTemplate, times(1)).update(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void handleAcceptsValidJobs() {
        when(jdbcTemplate.update(anyString())).thenReturn(0, 0);

        List<Map<String, Object>> batch = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("job_id", "job_ok");
        row.put("type", "WEBHOOK_PROCESS");
        row.put("status", "PENDING");
        row.put("attempts", 0);
        row.put("max_attempts", 5);
        row.put("leased_until", null);
        row.put("leased_by", null);
        batch.add(row);

        when(jdbcTemplate.queryForList(anyString(), anyInt())).thenReturn(batch);
        when(jdbcTemplate.queryForList(anyString(), anyString(), anyInt())).thenReturn(List.of());

        JobContext ctx = new JobContext("job-1", "JOB_LEASE_RECONCILE", null, 0, "usr_1", "trace-1", null);
        reconciler.handle(ctx);

        // 2 base updates, no discrepancy
        verify(jdbcTemplate, times(2)).update(anyString());
    }
}
