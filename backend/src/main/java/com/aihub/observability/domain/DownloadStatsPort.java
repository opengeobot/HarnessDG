/*
 * 功能: 下载统计查询端口——应用层聚合审计下载事件。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.observability.domain;

import java.util.List;

/**
 * 下载统计查询端口。
 */
public interface DownloadStatsPort {

    /** 统计单资产成功下载授权总次数。 */
    long countDownloadsByAsset(String assetId);

    /** 按版本聚合单资产下载次数（Top 20）。 */
    List<DownloadLeaderboardEntry> topVersionsByAsset(String assetId);

    /** 平台下载热度排行（按资产聚合）。 */
    List<DownloadLeaderboardEntry> topAssets(int limit);
}
