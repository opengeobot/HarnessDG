/*
 * 功能: 下载统计 REST 适配器——资产下载量与平台热度排行。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.observability.api;

import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.observability.application.DownloadStatsService;
import com.aihub.observability.domain.AssetDownloadStats;
import com.aihub.observability.domain.DownloadLeaderboardEntry;
import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 下载统计 REST 适配器。
 */
@RestController
public class DownloadStatsController {

    private final DownloadStatsService downloadStatsService;
    private final AuthorizationService authorizationService;

    public DownloadStatsController(DownloadStatsService downloadStatsService,
                                   AuthorizationService authorizationService) {
        this.downloadStatsService = downloadStatsService;
        this.authorizationService = authorizationService;
    }

    /** 查询单资产下载统计（asset:read）。 */
    @GetMapping("/api/v1/assets/{assetId}/stats")
    public ApiResponse<AssetDownloadStats> getAssetStats(@PathVariable String assetId) {
        authorizationService.requirePermission(Permissions.ASSET_READ);
        return respond(downloadStatsService.getAssetStats(assetId));
    }

    /** 查询平台下载热度排行（system:observe）。 */
    @GetMapping("/api/v1/system/metrics/downloads")
    public ApiResponse<List<DownloadLeaderboardEntry>> getDownloadLeaderboard(
            @RequestParam(required = false, defaultValue = "20") int limit) {
        authorizationService.requirePermission(Permissions.SYSTEM_OBSERVE);
        return respond(downloadStatsService.getDownloadLeaderboard(limit));
    }

    private static <T> ApiResponse<T> respond(T data) {
        String requestId = PrincipalContextHolder.current().map(PrincipalContext::requestId).orElse(null);
        String traceId = PrincipalContextHolder.current().map(PrincipalContext::traceId).orElse(null);
        return ApiResponse.of(data, requestId, traceId);
    }
}
