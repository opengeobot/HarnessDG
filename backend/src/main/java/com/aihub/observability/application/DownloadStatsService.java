/*
 * 功能: 下载量统计应用服务——基于 audit_log 下载授权事件聚合（DEC-016）。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.observability.application;

import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.observability.domain.AssetDownloadStats;
import com.aihub.observability.domain.DownloadLeaderboardEntry;
import com.aihub.observability.domain.DownloadStatsPort;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 下载量统计服务。
 *
 * <p>聚合 {@code DOWNLOAD_TICKET_ISSUED} 审计事件，按资产/版本统计成功授权次数。
 */
@Service
public class DownloadStatsService {

    private final DownloadStatsPort downloadStatsPort;
    private final AuthorizationService authorizationService;

    public DownloadStatsService(DownloadStatsPort downloadStatsPort,
                                AuthorizationService authorizationService) {
        this.downloadStatsPort = downloadStatsPort;
        this.authorizationService = authorizationService;
    }

    /** 查询单资产下载统计（需 asset:read）。 */
    public AssetDownloadStats getAssetStats(String assetId) {
        authorizationService.requirePermission(Permissions.ASSET_READ);

        long totalDownloads = downloadStatsPort.countDownloadsByAsset(assetId);
        List<DownloadLeaderboardEntry> byVersion = downloadStatsPort.topVersionsByAsset(assetId);

        return new AssetDownloadStats(assetId, totalDownloads, byVersion);
    }

    /** 查询平台下载热度排行（需 system:observe）。 */
    public List<DownloadLeaderboardEntry> getDownloadLeaderboard(int limit) {
        authorizationService.requirePermission(Permissions.SYSTEM_OBSERVE);
        int effective = Math.min(Math.max(limit, 1), 100);
        return downloadStatsPort.topAssets(effective);
    }
}
