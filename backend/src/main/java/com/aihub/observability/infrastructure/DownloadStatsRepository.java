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
 *
 * <p>查询预聚合的 {@code mv_download_stats} 物化视图，避免每次查询都 JOIN audit_log。
 */
@Repository
public class DownloadStatsRepository implements DownloadStatsPort {

    private final JdbcTemplate jdbcTemplate;

    public DownloadStatsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 统计单资产成功下载授权总次数。 */
    @Override
    public long countDownloadsByAsset(String assetId) {
        Long total = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(download_count), 0) FROM mv_download_stats WHERE asset_id = ?",
                Long.class, assetId);
        return total != null ? total : 0L;
    }

    /** 按版本聚合单资产下载次数（Top 20）。 */
    @Override
    public List<DownloadLeaderboardEntry> topVersionsByAsset(String assetId) {
        return jdbcTemplate.query(
                "SELECT version_id, version, download_count AS cnt "
                        + "FROM mv_download_stats "
                        + "WHERE asset_id = ? "
                        + "ORDER BY download_count DESC LIMIT 20",
                (rs, rowNum) -> new DownloadLeaderboardEntry(
                        rs.getString("version_id"),
                        rs.getString("version"),
                        rs.getLong("cnt")),
                assetId);
    }

    /** 平台下载热度排行（按资产聚合）。 */
    @Override
    public List<DownloadLeaderboardEntry> topAssets(int limit) {
        return jdbcTemplate.query(
                "SELECT asset_id, SUM(download_count) AS cnt "
                        + "FROM mv_download_stats "
                        + "GROUP BY asset_id ORDER BY cnt DESC LIMIT ?",
                (rs, rowNum) -> new DownloadLeaderboardEntry(
                        rs.getString("asset_id"),
                        null,
                        rs.getLong("cnt")),
                limit);
    }
}
