/*
 * 功能: 下载排行条目——资产或版本维度。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.observability.domain;

/**
 * 下载排行条目。
 *
 * @param id       资产 ID 或版本 ID
 * @param label    版本号等展示标签（可空）
 * @param downloads 下载授权次数
 */
public record DownloadLeaderboardEntry(String id, String label, long downloads) {}
