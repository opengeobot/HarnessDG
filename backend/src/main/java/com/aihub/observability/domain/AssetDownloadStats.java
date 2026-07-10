/*
 * 功能: 下载统计领域视图——资产级下载量与版本分布。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.observability.domain;

import java.util.List;

/**
 * 单资产下载统计视图。
 *
 * @param assetId         资产 ID
 * @param totalDownloads  累计下载授权次数
 * @param byVersion       按版本分布（最多 20 条）
 */
public record AssetDownloadStats(String assetId, long totalDownloads,
                                 List<DownloadLeaderboardEntry> byVersion) {}
