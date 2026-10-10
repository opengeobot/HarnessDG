/*
 * 功能: AssetRepositoryReconciler 单元测试。覆盖：批次处理、差异分类、格式校验、Gitea 存在性。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.integration.gitea.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aihub.job.domain.JobContext;
import com.aihub.job.domain.JobRepository;
import com.aihub.platform.observability.application.PlatformMetrics;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

class AssetRepositoryReconcilerTest {

    private JdbcTemplate jdbcTemplate;
    private PlatformMetrics platformMetrics;
    private ObjectProvider<GiteaRepositoryExistencePort> giteaExistenceProvider;
    private GiteaRepositoryExistencePort giteaExistencePort;
    private JobRepository jobRepository;
    private IdGenerator idGenerator;
    private AssetRepositoryReconciler reconciler;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        platformMetrics = mock(PlatformMetrics.class);
        giteaExistencePort = mock(GiteaRepositoryExistencePort.class);
        giteaExistenceProvider = mock(ObjectProvider.class);
        when(giteaExistenceProvider.getIfAvailable()).thenReturn(giteaExistencePort);
        jobRepository = mock(JobRepository.class);
        idGenerator = mock(IdGenerator.class);
        when(idGenerator.generate(IdPrefix.JOB)).thenReturn("job_reconcile");
        reconciler = new AssetRepositoryReconciler(jdbcTemplate, platformMetrics,
                giteaExistenceProvider, jobRepository, idGenerator);
    }

    @Test
    void typeReturnsAssetRepoReconcile() {
        assertThat(reconciler.type()).isEqualTo("ASSET_REPO_RECONCILE");
    }

    @Test
    void handleWithEmptyBatchDoesNotRecordDiscrepancy() {
        when(jdbcTemplate.queryForList(anyString(), anyInt())).thenReturn(List.of());

        JobContext ctx = new JobContext("job-1", "ASSET_REPO_RECONCILE", null, 0, "usr_1", "trace-1", null);
        reconciler.handle(ctx);

        verify(jdbcTemplate, times(1)).queryForList(anyString(), anyInt());
    }

    @Test
    void handleWithConsistentAssetsDoesNotRecordDiscrepancy() {
        List<Map<String, Object>> batch = new ArrayList<>();
        batch.add(row("ast_001", "my-model", "ai-lab"));
        batch.add(row("ast_002", "dataset-v2", "data-team"));

        when(jdbcTemplate.queryForList(anyString(), anyInt())).thenReturn(batch, List.of());
        when(giteaExistencePort.repositoryExists(anyString(), anyString())).thenReturn(true);

        JobContext ctx = new JobContext("job-1", "ASSET_REPO_RECONCILE", null, 0, "usr_1", "trace-1", null);
        reconciler.handle(ctx);

        verify(jdbcTemplate, never()).update(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void handleWithNullNameTriggersManualReview() {
        List<Map<String, Object>> batch = List.of(row("ast_001", null, "ai-lab"));
        when(jdbcTemplate.queryForList(anyString(), anyInt())).thenReturn(batch, List.of());

        JobContext ctx = new JobContext("job-1", "ASSET_REPO_RECONCILE", null, 0, "usr_1", "trace-1", null);
        reconciler.handle(ctx);

        verify(jdbcTemplate, times(1)).update(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void handleWithMissingGiteaRepoTriggersManualReviewAndReprovision() {
        List<Map<String, Object>> batch = List.of(row("ast_001", "my-model", "ai-lab"));
        when(jdbcTemplate.queryForList(anyString(), anyInt())).thenReturn(batch, List.of());
        when(giteaExistencePort.repositoryExists("ai-lab", "my-model")).thenReturn(false);

        JobContext ctx = new JobContext("job-1", "ASSET_REPO_RECONCILE", null, 0, "usr_1", "trace-1", null);
        reconciler.handle(ctx);

        verify(jdbcTemplate, times(1)).update(anyString(), anyString(), anyString(), anyString());
        verify(jobRepository).insert(any());
    }

    @Test
    void handleWithActiveReprovisionJobSkipsEnqueue() {
        // M3 去重：同一资产已有在途 REPOSITORY_PROVISION 时不得再次入队
        List<Map<String, Object>> batch = List.of(row("ast_001", "my-model", "ai-lab"));
        when(jdbcTemplate.queryForList(anyString(), anyInt())).thenReturn(batch, List.of());
        when(giteaExistencePort.repositoryExists("ai-lab", "my-model")).thenReturn(false);
        when(jobRepository.hasActiveByTypeAndAssetId("REPOSITORY_PROVISION", "ast_001")).thenReturn(true);

        JobContext ctx = new JobContext("job-1", "ASSET_REPO_RECONCILE", null, 0, "usr_1", "trace-1", null);
        reconciler.handle(ctx);

        verify(jobRepository, never()).insert(any());
        // 差异仍须如实记录
        verify(jdbcTemplate, times(1)).update(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void handleUsesRepoFullNameProjectionOverNamespace() {
        // M2 权威投影：repo_full_name 与 namespace/name 拼接不一致时，以投影拆分结果查 Gitea
        Map<String, Object> projected = row("ast_001", "my-model", "ai-lab");
        projected.put("repo_full_name", "org-real/my-model");
        when(jdbcTemplate.queryForList(anyString(), anyInt())).thenReturn(List.of(projected), List.of());
        when(giteaExistencePort.repositoryExists("org-real", "my-model")).thenReturn(true);

        JobContext ctx = new JobContext("job-1", "ASSET_REPO_RECONCILE", null, 0, "usr_1", "trace-1", null);
        reconciler.handle(ctx);

        verify(giteaExistencePort).repositoryExists("org-real", "my-model");
        verify(giteaExistencePort, never()).repositoryExists(eq("ai-lab"), anyString());
        verify(jdbcTemplate, never()).update(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void handleWithInvalidProjectionTriggersManualReview() {
        // 投影存在但不是合法 owner/repo：判人工复核，不猜测发起 Gitea 请求
        Map<String, Object> bad = row("ast_001", "my-model", "ai-lab");
        bad.put("repo_full_name", "not-a-valid-full-name");
        when(jdbcTemplate.queryForList(anyString(), anyInt())).thenReturn(List.of(bad), List.of());

        JobContext ctx = new JobContext("job-1", "ASSET_REPO_RECONCILE", null, 0, "usr_1", "trace-1", null);
        reconciler.handle(ctx);

        verify(giteaExistencePort, never()).repositoryExists(anyString(), anyString());
        verify(jdbcTemplate, times(1)).update(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void checkAssetRepositorySkipsGiteaWhenPortUnavailable() {
        when(giteaExistenceProvider.getIfAvailable()).thenReturn(null);

        AssetRepositoryReconciler local = new AssetRepositoryReconciler(jdbcTemplate, platformMetrics,
                giteaExistenceProvider, jobRepository, idGenerator);

        assertThat(local.checkAssetRepository("ast_001", "valid-name", "ai-lab", null))
                .isEqualTo(AssetRepositoryReconciler.ReconcileResult.CONSISTENT);
        verify(giteaExistencePort, never()).repositoryExists(anyString(), anyString());
    }

    @Test
    void handleWithInvalidNameFormatTriggersManualReview() {
        List<Map<String, Object>> batch = List.of(row("ast_001", "INVALID NAME!", "ai-lab"));
        when(jdbcTemplate.queryForList(anyString(), anyInt())).thenReturn(batch, List.of());

        JobContext ctx = new JobContext("job-1", "ASSET_REPO_RECONCILE", null, 0, "usr_1", "trace-1", null);
        reconciler.handle(ctx);

        verify(jdbcTemplate, times(1)).update(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void handleWithNullNamespaceTriggersAutoRepair() {
        List<Map<String, Object>> batch = List.of(row("ast_001", "valid-name", null));
        when(jdbcTemplate.queryForList(anyString(), anyInt())).thenReturn(batch, List.of());

        JobContext ctx = new JobContext("job-1", "ASSET_REPO_RECONCILE", null, 0, "usr_1", "trace-1", null);
        reconciler.handle(ctx);

        verify(jdbcTemplate, times(1)).update(anyString(), anyString(), anyString(), anyString());
    }

    private Map<String, Object> row(String assetId, String name, String namespace) {
        Map<String, Object> m = new HashMap<>();
        m.put("asset_id", assetId);
        m.put("name", name);
        m.put("namespace", namespace);
        // 与扫描 SQL 一致：默认无权威投影（回退 namespace/name 推导口径）
        m.put("repo_full_name", null);
        m.put("provisioning_status", "COMPLETED");
        return m;
    }
}
