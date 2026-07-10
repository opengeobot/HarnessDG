/*
 * 功能: PublishedVersionReconciler 单元测试——PG 格式校验与 Gitea Tag 校验。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.integration.gitea.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aihub.job.domain.JobContext;
import com.aihub.platform.observability.application.PlatformMetrics;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

class PublishedVersionReconcilerTest {

    private JdbcTemplate jdbcTemplate;
    private PlatformMetrics platformMetrics;
    private ObjectProvider<GiteaTagVerificationPort> giteaTagProvider;
    private GiteaTagVerificationPort giteaTagPort;
    private PublishedVersionReconciler reconciler;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        platformMetrics = mock(PlatformMetrics.class);
        giteaTagPort = mock(GiteaTagVerificationPort.class);
        giteaTagProvider = mock(ObjectProvider.class);
        when(giteaTagProvider.getIfAvailable()).thenReturn(giteaTagPort);
        reconciler = new PublishedVersionReconciler(jdbcTemplate, platformMetrics, giteaTagProvider);
    }

    @Test
    void typeReturnsPublishedVersionReconcile() {
        assertThat(reconciler.type()).isEqualTo("PUBLISHED_VERSION_RECONCILE");
    }

    @Test
    void checkPublishedVersionConsistentWhenGiteaTagMatches() {
        when(giteaTagPort.verifyTag("ai-lab", "my-model", "v1.0.0", "a".repeat(40)))
                .thenReturn(GiteaTagVerificationPort.TagVerifyResult.MATCHES);

        Map<String, Object> row = validRow();
        assertThat(reconciler.checkPublishedVersion(row))
                .isEqualTo(PublishedVersionReconciler.ReconcileResult.CONSISTENT);
    }

    @Test
    void checkPublishedVersionManualReviewWhenGiteaTagMissing() {
        when(giteaTagPort.verifyTag("ai-lab", "my-model", "v1.0.0", "a".repeat(40)))
                .thenReturn(GiteaTagVerificationPort.TagVerifyResult.MISSING_TAG);

        assertThat(reconciler.checkPublishedVersion(validRow()))
                .isEqualTo(PublishedVersionReconciler.ReconcileResult.MANUAL_REVIEW);
    }

    @Test
    void checkPublishedVersionSecurityIncidentWhenCommitMismatch() {
        when(giteaTagPort.verifyTag("ai-lab", "my-model", "v1.0.0", "a".repeat(40)))
                .thenReturn(GiteaTagVerificationPort.TagVerifyResult.COMMIT_MISMATCH);

        assertThat(reconciler.checkPublishedVersion(validRow()))
                .isEqualTo(PublishedVersionReconciler.ReconcileResult.SECURITY_INCIDENT);
    }

    @Test
    void checkPublishedVersionSkipsGiteaWhenPortUnavailable() {
        when(giteaTagProvider.getIfAvailable()).thenReturn(null);

        PublishedVersionReconciler local = new PublishedVersionReconciler(jdbcTemplate, platformMetrics, giteaTagProvider);
        assertThat(local.checkPublishedVersion(validRow()))
                .isEqualTo(PublishedVersionReconciler.ReconcileResult.CONSISTENT);
        verify(giteaTagPort, never()).verifyTag(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void handleWithEmptyBatchCompletes() {
        when(jdbcTemplate.queryForList(anyString(), anyInt())).thenReturn(List.of());

        JobContext ctx = new JobContext("job-1", "PUBLISHED_VERSION_RECONCILE", null, 0, "usr_1", "trace-1", null);
        reconciler.handle(ctx);

        verify(jdbcTemplate).queryForList(anyString(), anyInt());
    }

    private Map<String, Object> validRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("version_id", "ver_001");
        row.put("asset_id", "ast_001");
        row.put("git_tag", "v1.0.0");
        row.put("manifest_digest", "a".repeat(64));
        row.put("source_commit", "a".repeat(40));
        row.put("namespace", "ai-lab");
        row.put("name", "my-model");
        return row;
    }
}
