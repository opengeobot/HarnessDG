/*
 * 功能: PhysicalRecycleJobHandler 单元测试（TASK-P0BR-064）——固化 recycled_at
 *       由 SQL now() 求值、不再绑定 java.time.Instant 的防回归断言，并覆盖保留期前置。
 * 时间: 2026-09-21
 * 作者: AxeXie
 */
package com.aihub.asset.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aihub.audit.application.AuditService;
import com.aihub.integration.gitea.infrastructure.GiteaAssetRepositoryProvisioner;
import com.aihub.integration.minio.infrastructure.MinioStorageAdapter;
import com.aihub.job.domain.JobContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 背景：JdbcTemplate 无法为 java.time.Instant 实参推断 SQL 类型
 * （TASK-P0BR-063 §2 事实 7 同型缺陷）；回收路径在标记 recycled_at 处必然抛异常。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PhysicalRecycleJobHandler")
class PhysicalRecycleJobHandlerTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private AuditService auditService;
    @Mock private ObjectProvider<GiteaAssetRepositoryProvisioner> giteaProvider;
    @Mock private ObjectProvider<MinioStorageAdapter> minioProvider;

    private PhysicalRecycleJobHandler handler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handler = new PhysicalRecycleJobHandler(jdbcTemplate, objectMapper, auditService,
                giteaProvider, minioProvider);
    }

    private JobContext context(String assetId, int retentionDays) throws Exception {
        return new JobContext("job_01", "ASSET_PHYSICAL_RECYCLE",
                objectMapper.writeValueAsString(Map.of("assetId", assetId, "retentionDays", retentionDays)),
                0, "usr_ops", "trace_01", assetId);
    }

    @Test
    @DisplayName("保留期已过：recycled_at 由 SQL now() 求值且无任何 Instant 参数绑定")
    void recyclesWithDbEvaluatedTimestamp() throws Exception {
        Instant archivedAt = Instant.now().minus(120, ChronoUnit.DAYS);
        when(jdbcTemplate.queryForMap(
                eq("SELECT status, archived_at FROM asset WHERE asset_id = ?"), eq("ast_01")))
                .thenReturn(Map.of("status", "ARCHIVED", "archived_at", Timestamp.from(archivedAt)));
        when(jdbcTemplate.queryForObject(
                eq("SELECT repo_full_name FROM asset WHERE asset_id = ?"), eq(String.class), eq("ast_01")))
                .thenReturn(null);

        handler.handle(context("ast_01", 90));

        verify(jdbcTemplate).update(
                eq("UPDATE asset SET recycled_at = now() WHERE asset_id = ?"), eq("ast_01"));

        // 防回归守卫：任何 update 调用都不得绑定 Instant 参数
        List<Object> allArgs = mockingDetails(jdbcTemplate).getInvocations().stream()
                .filter(i -> "update".equals(i.getMethod().getName()))
                .flatMap(i -> List.of(i.getArguments()).stream())
                .toList();
        assertThat(allArgs).noneMatch(a -> a instanceof Instant);
    }

    @Test
    @DisplayName("保留期未过：不回收、不写 recycled_at、不记审计")
    void skipsWhenRetentionNotExpired() throws Exception {
        Instant archivedAt = Instant.now().minus(1, ChronoUnit.DAYS);
        when(jdbcTemplate.queryForMap(
                eq("SELECT status, archived_at FROM asset WHERE asset_id = ?"), eq("ast_02")))
                .thenReturn(Map.of("status", "ARCHIVED", "archived_at", Timestamp.from(archivedAt)));

        handler.handle(context("ast_02", 90));

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
        verify(auditService, never()).record(any());
    }
}
