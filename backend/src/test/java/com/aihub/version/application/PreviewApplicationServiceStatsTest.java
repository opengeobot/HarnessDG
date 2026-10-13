/*
 * 功能: getPreview 的 Split 统计组装契约测试（TASK-P0BR-078）——
 *       完整 JSON 结构透传、NULL/blank 降级未知、漂移/损坏 JSON 不回归样例渲染。
 * 时间: 2026-10-10
 * 作者: AxeXie
 */
package com.aihub.version.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.aihub.authorization.application.AuthorizationService;
import com.aihub.job.application.JobApplicationService;
import com.aihub.platform.observability.application.PlatformMetrics;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import com.aihub.transfer.domain.StoragePort;
import com.aihub.version.domain.PreviewQueryPort;
import com.aihub.version.domain.VersionRepository;
import com.aihub.version.infrastructure.PreviewProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PreviewApplicationService Split 统计组装（TASK-P0BR-078）")
class PreviewApplicationServiceStatsTest {

    @Mock private JobApplicationService jobApplicationService;
    @Mock private PreviewQueryPort previewQueryPort;
    @Mock private AuthorizationService authorizationService;
    @Mock private VersionRepository versionRepository;
    @Mock private StoragePort storagePort;
    @Mock private PlatformMetrics platformMetrics;
    @Mock private ObjectProvider<PlatformMetrics> platformMetricsProvider;

    private PreviewApplicationService service;

    @BeforeEach
    void setUp() {
        when(platformMetricsProvider.getIfAvailable()).thenReturn(platformMetrics);
        service = new PreviewApplicationService(jobApplicationService, new ObjectMapper(),
                previewQueryPort, authorizationService, versionRepository, storagePort,
                new PreviewProperties(100, 50, 1024L * 1024, 5L * 1024 * 1024, 1024L * 1024,
                        null, 0, 0, 0L),
                platformMetricsProvider);
    }

    private void stubRecord(String splitStatsJson) {
        when(previewQueryPort.findLatestPreview("ast_01", null)).thenReturn(
                Optional.of(new PreviewQueryPort.PreviewRecord(
                        "prv_01", "text/csv", "{\"columns\":[\"a\"]}", "2026-10-10T00:00:00Z", splitStatsJson)));
    }

    @Test
    @DisplayName("完整 split_stats JSON → splits/totals/generatedFrom 结构透传")
    void fullStatsJsonIsProjectedToView() {
        stubRecord("""
                {"splits":[{"name":"train","rowCount":3,"fileCount":2,"sizeBytes":64,
                "columns":["a","b"],"truncated":false}],
                "totals":{"rowCount":3,"fileCount":2,"sizeBytes":64,"truncated":false},
                "generatedFrom":{"versionId":"ver_01","sourceCommit":"aaa","generatedAt":"2026-10-10T00:00:00Z"}}""");

        var view = service.getPreview("ast_01", null);

        assertThat(view.previewId()).isEqualTo("prv_01");
        assertThat(view.content()).isEqualTo("{\"columns\":[\"a\"]}");
        assertThat(view.splits()).hasSize(1);
        assertThat(view.splits().get(0))
                .containsEntry("name", "train")
                .containsEntry("rowCount", 3)
                .containsEntry("fileCount", 2)
                .containsEntry("truncated", false);
        assertThat(view.totals()).containsEntry("rowCount", 3);
        assertThat(view.generatedFrom()).containsEntry("versionId", "ver_01");
    }

    @Test
    @DisplayName("split_stats 为 NULL/blank（旧投影）→ 三分量同时为 null = 统计未知")
    void nullOrBlankStatsDegradeToUnknownWithoutZeroFabrication() {
        for (String raw : new String[] {null, "", "   "}) {
            stubRecord(raw);
            var view = service.getPreview("ast_01", null);
            assertThat(view.splits()).isNull();
            assertThat(view.totals()).isNull();
            assertThat(view.generatedFrom()).isNull();
        }
    }

    @Test
    @DisplayName("损坏或结构漂移 JSON → 降级未知，样例渲染不回归")
    void corruptOrDriftedStatsDegradeToUnknown() {
        for (String raw : new String[] {
                "{not json",
                "[1,2,3]",
                "{\"splits\":\"not-an-array\",\"totals\":[],\"generatedFrom\":3}"}) {
            stubRecord(raw);
            var view = service.getPreview("ast_01", null);
            assertThat(view.content()).as("样例内容保持可渲染: " + raw).isEqualTo("{\"columns\":[\"a\"]}");
            assertThat(view.splits()).isNull();
            assertThat(view.totals()).isNull();
            assertThat(view.generatedFrom()).isNull();
        }
    }

    @Test
    @DisplayName("空 splits 数组视为未知（不输出空列表）")
    void emptySplitsArrayIsTreatedAsUnknown() {
        stubRecord("{\"splits\":[],\"totals\":null,\"generatedFrom\":null}");

        var view = service.getPreview("ast_01", null);

        assertThat(view.splits()).isNull();
        assertThat(view.totals()).isNull();
    }

    @Test
    @DisplayName("无预览投影 → 404 ASSET_NOT_FOUND（沿用 068 语义）")
    void missingPreviewThrowsNotFound() {
        when(previewQueryPort.findLatestPreview("ast_01", null)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPreview("ast_01", null))
                .isInstanceOf(NotFoundException.class)
                .extracting(e -> ((NotFoundException) e).errorCode())
                .isEqualTo(ErrorCode.ASSET_NOT_FOUND);
    }
}
