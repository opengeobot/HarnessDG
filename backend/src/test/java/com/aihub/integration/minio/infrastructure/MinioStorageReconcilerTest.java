/*
 * 功能: MinioStorageReconciler 单元测试。覆盖过期 Session 清理、artifact 路径安全校验。
 * 时间: 2026-07-04
 */
package com.aihub.integration.minio.infrastructure;

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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

class MinioStorageReconcilerTest {

    private JdbcTemplate jdbcTemplate;
    private PlatformMetrics platformMetrics;
    private ObjectProvider<MinioObjectExistencePort> minioExistenceProvider;
    private MinioObjectExistencePort minioExistencePort;
    private MinioStorageReconciler reconciler;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        platformMetrics = mock(PlatformMetrics.class);
        minioExistencePort = mock(MinioObjectExistencePort.class);
        minioExistenceProvider = mock(ObjectProvider.class);
        when(minioExistenceProvider.getIfAvailable()).thenReturn(minioExistencePort);
        reconciler = new MinioStorageReconciler(jdbcTemplate, platformMetrics, minioExistenceProvider);
    }

    @Test
    void typeReturnsMinioStorageReconcile() {
        assertThat(reconciler.type()).isEqualTo("MINIO_STORAGE_RECONCILE");
    }

    @Test
    void handleExpiresStaleOpenSessions() {
        when(jdbcTemplate.update(anyString())).thenReturn(2, 0);
        when(jdbcTemplate.queryForList(anyString(), anyInt())).thenReturn(List.of());

        JobContext ctx = new JobContext("job-1", "MINIO_STORAGE_RECONCILE", null, 0, "usr_1", "trace-1", null);
        reconciler.handle(ctx);

        verify(jdbcTemplate, times(2)).update(anyString());
    }

    @Test
    void handleWithEmptyArtifactsDoesNotRecordDiscrepancy() {
        when(jdbcTemplate.update(anyString())).thenReturn(0, 0);
        when(jdbcTemplate.queryForList(anyString(), anyInt())).thenReturn(List.of());

        JobContext ctx = new JobContext("job-1", "MINIO_STORAGE_RECONCILE", null, 0, "usr_1", "trace-1", null);
        reconciler.handle(ctx);

        verify(jdbcTemplate, times(2)).update(anyString());
    }

    @Test
    void handleDetectsPathTraversalAsSecurityIncident() {
        when(jdbcTemplate.update(anyString())).thenReturn(0, 0);

        List<Map<String, Object>> batch = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("artifact_id", "art_001");
        row.put("version_id", "ver_001");
        row.put("path", "../../../etc/passwd");
        row.put("sha256", null);
        row.put("size", 100L);
        row.put("media_type", "application/octet-stream");
        row.put("asset_id", "ast_001");
        row.put("version", "v1.0.0");
        batch.add(row);

        when(jdbcTemplate.queryForList(anyString(), anyInt())).thenReturn(batch);
        when(jdbcTemplate.queryForList(anyString(), anyString(), anyInt())).thenReturn(List.of());

        JobContext ctx = new JobContext("job-1", "MINIO_STORAGE_RECONCILE", null, 0, "usr_1", "trace-1", null);
        reconciler.handle(ctx);

        // 1 discrepancy INSERT (4-arg update)
        verify(jdbcTemplate, times(1)).update(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void handleDetectsMissingMinioObject() {
        when(jdbcTemplate.update(anyString())).thenReturn(0, 0);
        when(minioExistencePort.objectExists(anyString(), anyString())).thenReturn(false);

        List<Map<String, Object>> batch = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("artifact_id", "art_003");
        row.put("version_id", "ver_001");
        row.put("path", "data/train.csv");
        row.put("sha256", "a".repeat(64));
        row.put("size", 1024L);
        row.put("media_type", "text/csv");
        row.put("asset_id", "ast_001");
        row.put("version", "v1.0.0");
        batch.add(row);

        when(jdbcTemplate.queryForList(anyString(), anyInt())).thenReturn(batch);
        when(jdbcTemplate.queryForList(anyString(), anyString(), anyInt())).thenReturn(List.of());

        JobContext ctx = new JobContext("job-1", "MINIO_STORAGE_RECONCILE", null, 0, "usr_1", "trace-1", null);
        reconciler.handle(ctx);

        verify(jdbcTemplate, times(1)).update(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void handleAcceptsValidArtifacts() {
        when(jdbcTemplate.update(anyString())).thenReturn(0, 0);
        when(minioExistencePort.objectExists(anyString(), anyString())).thenReturn(true);

        List<Map<String, Object>> batch = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("artifact_id", "art_002");
        row.put("version_id", "ver_001");
        row.put("path", "data/train.csv");
        row.put("sha256", "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890");
        row.put("size", 1024L);
        row.put("media_type", "text/csv");
        row.put("asset_id", "ast_001");
        row.put("version", "v1.0.0");
        batch.add(row);

        when(jdbcTemplate.queryForList(anyString(), anyInt())).thenReturn(batch);
        when(jdbcTemplate.queryForList(anyString(), anyString(), anyInt())).thenReturn(List.of());

        JobContext ctx = new JobContext("job-1", "MINIO_STORAGE_RECONCILE", null, 0, "usr_1", "trace-1", null);
        reconciler.handle(ctx);

        // No discrepancy INSERT
        verify(jdbcTemplate, times(2)).update(anyString());
    }
}
