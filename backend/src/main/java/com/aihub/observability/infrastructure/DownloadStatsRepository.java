/*
 * 功能: 下载量统计 JDBC 仓储——聚合 DOWNLOAD_TICKET_ISSUED 审计事件。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.observability.infrastructure;

import com.aihub.observability.domain.DownloadLeaderboardEntry;
import com.aihub.observability.domain.DownloadStatsPort;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 下载统计查询仓储。
 */
@Repository
public class DownloadStatsRepository implements DownloadStatsPort {

    private static final String DOWNLOAD_EVENT = "DOWNLOAD_TICKET_ISSUED";

    private final JdbcTemplate jdbcTemplate;

    public DownloadStatsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 统计单资产成功下载授权总次数。 */
    @Override
    public long countDownloadsByAsset(String assetId) {
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log al "
                        + "JOIN asset_version av ON al.resource_id = av.version_id "
                        + "WHERE al.event_type = ? AND al.result = 'SUCCEEDED' AND av.asset_id = ?",
                Long.class, DOWNLOAD_EVENT, assetId);
        return total != null ? total : 0L;
    }

    /** 按版本聚合单资产下载次数（Top 20）。 */
    @Override
    public List<DownloadLeaderboardEntry> topVersionsByAsset(String assetId) {
        return jdbcTemplate.query(
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
    }

    /** 平台下载热度排行（按资产聚合）。 */
    @Override
    public List<DownloadLeaderboardEntry> topAssets(int limit) {
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
                DOWNLOAD_EVENT, limit);
    }
}
