/*
 * 功能: DownloadStatsService 单元测试——审计聚合与授权校验。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.observability.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.shared.error.AuthorizationException;
import com.aihub.observability.domain.AssetDownloadStats;
import com.aihub.observability.domain.DownloadStatsPort;
import com.aihub.shared.error.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DownloadStatsServiceTest {

    private DownloadStatsPort downloadStatsPort;
    private AuthorizationService authorizationService;
    private DownloadStatsService service;

    @BeforeEach
    void setUp() {
        downloadStatsPort = mock(DownloadStatsPort.class);
        authorizationService = mock(AuthorizationService.class);
        service = new DownloadStatsService(downloadStatsPort, authorizationService);
    }

    @Test
    void getAssetStatsRequiresAssetRead() {
        doThrow(new AuthorizationException(ErrorCode.AUTH_PERMISSION_DENIED, "denied", java.util.Map.of()))
                .when(authorizationService).requirePermission(Permissions.ASSET_READ);

        org.junit.jupiter.api.Assertions.assertThrows(AuthorizationException.class,
                () -> service.getAssetStats("ast_001"));
    }

    @Test
    void getAssetStatsAggregatesDownloadEvents() {
        when(downloadStatsPort.countDownloadsByAsset("ast_001")).thenReturn(42L);
        when(downloadStatsPort.topVersionsByAsset("ast_001")).thenReturn(List.of());

        AssetDownloadStats stats = service.getAssetStats("ast_001");

        assertThat(stats.assetId()).isEqualTo("ast_001");
        assertThat(stats.totalDownloads()).isEqualTo(42L);
        verify(authorizationService).requirePermission(Permissions.ASSET_READ);
    }

    @Test
    void getDownloadLeaderboardRequiresSystemObserve() {
        when(downloadStatsPort.topAssets(10)).thenReturn(List.of());

        service.getDownloadLeaderboard(10);
        verify(authorizationService).requirePermission(Permissions.SYSTEM_OBSERVE);
    }
}
