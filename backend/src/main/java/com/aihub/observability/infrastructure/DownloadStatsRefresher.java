/*
 * 功能: 下载统计物化视图定期刷新——每 5 分钟并发刷新 mv_download_stats。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.observability.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定期刷新下载统计物化视图 {@code mv_download_stats}。
 *
 * <p>使用 {@code REFRESH MATERIALIZED VIEW CONCURRENTLY} 避免阻塞读查询，
 * 每 5 分钟执行一次（可通过配置 {@code aihub.download-stats.refresh-interval} 调整）。
 */
@Component
public class DownloadStatsRefresher {

    private static final Logger LOG = LoggerFactory.getLogger(DownloadStatsRefresher.class);

    private final JdbcTemplate jdbcTemplate;

    public DownloadStatsRefresher(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(fixedDelayString = "${aihub.download-stats.refresh-interval:300000}")
    public void refreshMaterializedView() {
        try {
            jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_download_stats");
            LOG.debug("mv_download_stats refreshed successfully");
        } catch (Exception e) {
            LOG.warn("Failed to refresh mv_download_stats: {}", e.getMessage());
        }
    }
}
