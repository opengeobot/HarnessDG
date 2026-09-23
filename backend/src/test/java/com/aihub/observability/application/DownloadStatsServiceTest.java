/*
 * 功能: DownloadStatsService 单元测试——审计聚合与授权校验。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.observability.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aihub.asset.application.AssetAccessPolicy;
import com.aihub.asset.domain.Asset;
import com.aihub.asset.domain.AssetRepository;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.shared.error.AuthorizationException;
import com.aihub.observability.domain.AssetDownloadStats;
import com.aihub.observability.domain.DownloadStatsPort;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DownloadStatsServiceTest {

    private DownloadStatsPort downloadStatsPort;
    private AuthorizationService authorizationService;
    private AssetRepository assetRepository;
    private AssetAccessPolicy assetAccessPolicy;
    private DownloadStatsService service;

    @BeforeEach
    void setUp() {
        downloadStatsPort = mock(DownloadStatsPort.class);
        authorizationService = mock(AuthorizationService.class);
        assetRepository = mock(AssetRepository.class);
        assetAccessPolicy = mock(AssetAccessPolicy.class);
        service = new DownloadStatsService(downloadStatsPort, authorizationService,
                assetRepository, assetAccessPolicy);
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
        Asset asset = mock(Asset.class);
        when(asset.assetId()).thenReturn("ast_001");
        when(assetRepository.findByAssetId("ast_001")).thenReturn(Optional.of(asset));
        when(assetAccessPolicy.canAccess(any(), any())).thenReturn(true);
        when(downloadStatsPort.countDownloadsByAsset("ast_001")).thenReturn(42L);
        when(downloadStatsPort.topVersionsByAsset("ast_001")).thenReturn(List.of());

        AssetDownloadStats stats = service.getAssetStats("ast_001");

        assertThat(stats.assetId()).isEqualTo("ast_001");
        assertThat(stats.totalDownloads()).isEqualTo(42L);
        verify(authorizationService).requirePermission(Permissions.ASSET_READ);
    }

    /** AC-P5-OBS-001：无权主体不得枚举他人资产下载行为——不可见资产返回 NOT_FOUND 且零聚合查询。 */
    @Test
    void getAssetStatsHidesInaccessibleAsset() {
        Asset asset = mock(Asset.class);
        when(assetRepository.findByAssetId("ast_private")).thenReturn(Optional.of(asset));
        when(assetAccessPolicy.canAccess(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.getAssetStats("ast_private"))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(downloadStatsPort);
    }

    /** 资产不存在同样返回 NOT_FOUND，与不可见路径同错误码，防枚举探测。 */
    @Test
    void getAssetStatsUnknownAssetReturnsNotFound() {
        when(assetRepository.findByAssetId(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAssetStats("ast_missing"))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(downloadStatsPort);
    }

    @Test
    void getDownloadLeaderboardRequiresSystemObserve() {
        when(downloadStatsPort.topAssets(10)).thenReturn(List.of());

        service.getDownloadLeaderboard(10);
        verify(authorizationService).requirePermission(Permissions.SYSTEM_OBSERVE);
    }
}
