/*
 * 功能: 下载量统计应用服务——基于 audit_log 下载授权事件聚合（DEC-016）。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.observability.application;

import com.aihub.asset.application.AssetAccessPolicy;
import com.aihub.asset.domain.Asset;
import com.aihub.asset.domain.AssetRepository;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.observability.domain.AssetDownloadStats;
import com.aihub.observability.domain.DownloadLeaderboardEntry;
import com.aihub.observability.domain.DownloadStatsPort;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import java.util.List;
import java.util.Map;
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
    private final AssetRepository assetRepository;
    private final AssetAccessPolicy assetAccessPolicy;

    public DownloadStatsService(DownloadStatsPort downloadStatsPort,
                                AuthorizationService authorizationService,
                                AssetRepository assetRepository,
                                AssetAccessPolicy assetAccessPolicy) {
        this.downloadStatsPort = downloadStatsPort;
        this.authorizationService = authorizationService;
        this.assetRepository = assetRepository;
        this.assetAccessPolicy = assetAccessPolicy;
    }

    /**
     * 查询单资产下载统计（需 asset:read 且主体可访问该资产）。
     *
     * <p>AC-P5-OBS-001：无权主体不得枚举他人资产的下载行为——不可见资产统一返回
     * {@code ASSET_NOT_FOUND}，与 {@code AssetApplicationService.loadAccessible} 同语义。
     */
    public AssetDownloadStats getAssetStats(String assetId) {
        authorizationService.requirePermission(Permissions.ASSET_READ);
        String principalId = PrincipalContextHolder.current()
                .map(PrincipalContext::principalId).orElse(null);
        Asset asset = assetRepository.findByAssetId(assetId)
                .filter(found -> assetAccessPolicy.canAccess(found, principalId))
                .orElseThrow(() -> new NotFoundException(ErrorCode.ASSET_NOT_FOUND,
                        "asset not found: " + assetId, Map.of()));

        long totalDownloads = downloadStatsPort.countDownloadsByAsset(asset.assetId());
        List<DownloadLeaderboardEntry> byVersion = downloadStatsPort.topVersionsByAsset(asset.assetId());

        return new AssetDownloadStats(assetId, totalDownloads, byVersion);
    }

    /** 查询平台下载热度排行（需 system:observe）。 */
    public List<DownloadLeaderboardEntry> getDownloadLeaderboard(int limit) {
        authorizationService.requirePermission(Permissions.SYSTEM_OBSERVE);
        int effective = Math.min(Math.max(limit, 1), 100);
        return downloadStatsPort.topAssets(effective);
    }
}
