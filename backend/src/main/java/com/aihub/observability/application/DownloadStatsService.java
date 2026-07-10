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
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 下载量统计服务。
 *
 * <p>聚合 {@code DOWNLOAD_TICKET_ISSUED} 审计事件，按资产/版本统计成功授权次数。
 */
@Service
public class DownloadStatsService {

    private static final String DOWNLOAD_EVENT = "DOWNLOAD_TICKET_ISSUED";

    private final JdbcTemplate jdbcTemplate;
    private final AuthorizationService authorizationService;

    public DownloadStatsService(JdbcTemplate jdbcTemplate, AuthorizationService authorizationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.authorizationService = authorizationService;
    }

    /** 查询单资产下载统计（需 asset:read）。 */
    public AssetDownloadStats getAssetStats(String assetId) {
        authorizationService.requirePermission(Permissions.ASSET_READ);

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log al "
                        + "JOIN asset_version av ON al.resource_id = av.version_id "
                        + "WHERE al.event_type = ? AND al.result = 'SUCCEEDED' AND av.asset_id = ?",
                Long.class, DOWNLOAD_EVENT, assetId);
        long totalDownloads = total != null ? total : 0L;

        List<DownloadLeaderboardEntry> byVersion = jdbcTemplate.query(
                "SELECT av.version_id, av.version, COUNT(*) AS cnt "
                        + "FROM audit_log al "
                        + "JOIN asset_version av ON al.resource_id = av.version_id "
                        + "WHERE al.event_type = ? AND al.result = 'SUCCEEDED' AND av.asset_id = ? "
                        + "GROUP BY av.version_id, av.version ORDER BY cnt DESC LIMIT 20",
                (rs, rowNum) -> new DownloadLeaderboardEntry(
                        rs.getString("version_id"),
                        rs.getString("version"),
                        rs.getLong("cnt")),
                DOWNLOAD_EVENT, assetId);

        return new AssetDownloadStats(assetId, totalDownloads, byVersion);
    }

    /** 查询平台下载热度排行（需 system:observe）。 */
    public List<DownloadLeaderboardEntry> getDownloadLeaderboard(int limit) {
        authorizationService.requirePermission(Permissions.SYSTEM_OBSERVE);
        int effective = Math.min(Math.max(limit, 1), 100);
        return jdbcTemplate.query(
                "SELECT av.asset_id, COUNT(*) AS cnt "
                        + "FROM audit_log al "
                        + "JOIN asset_version av ON al.resource_id = av.version_id "
                        + "WHERE al.event_type = ? AND al.result = 'SUCCEEDED' "
                        + "GROUP BY av.asset_id ORDER BY cnt DESC LIMIT ?",
                (rs, rowNum) -> new DownloadLeaderboardEntry(
                        rs.getString("asset_id"),
                        null,
                        rs.getLong("cnt")),
                DOWNLOAD_EVENT, effective);
    }
}
