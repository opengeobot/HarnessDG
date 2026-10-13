/*
 * 功能: 预览 Worker 资源隔离配置（行数/字节上限 + Split 统计上限）。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.version.infrastructure;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 预览生成资源上限（前缀 {@code aihub.preview}）。
 *
 * <p>Worker 进程级内存/CPU 隔离由容器 cgroup 或 JVM 堆限制承担；本配置约束单次预览内容解析上限。
 *
 * @param maxRows        最大预览行数
 * @param maxCols        最大预览列数
 * @param maxBytes       预览内容最大字节数
 * @param maxRenderBytes 图片预览代理渲染最大字节数（TASK-P0BR-068，默认 5 MiB）
 * @param maxTextBytes   文本工件内容代理读取最大字节数（TASK-P0BR-077，默认 1 MiB）
 * @param splitDirs      Split 目录白名单（artifact path 顶层目录匹配，其余归 default；TASK-P0BR-078）
 * @param maxStatsRows   单文件统计采样行数上限（TASK-P0BR-078，默认 10000）
 * @param maxStatsFiles  单 Split 统计最大文件数上限（TASK-P0BR-078，默认 200）
 * @param maxStatsBytes  单 Split 统计累计读取字节上限（TASK-P0BR-078，默认 64 MiB）
 */
@ConfigurationProperties(prefix = "aihub.preview")
public record PreviewProperties(int maxRows, int maxCols, long maxBytes, long maxRenderBytes,
                                long maxTextBytes, List<String> splitDirs, int maxStatsRows,
                                int maxStatsFiles, long maxStatsBytes) {

    /** 缺省 Split 目录白名单（配置缺失/非法时回退，规则变更须经规格变更，TASK-P0BR-078 CONFIG）。 */
    public static final List<String> DEFAULT_SPLIT_DIRS = List.of("train", "validation", "val", "test", "dev");

    public PreviewProperties {
        if (maxRows <= 0) {
            maxRows = 100;
        }
        if (maxCols <= 0) {
            maxCols = 50;
        }
        if (maxBytes <= 0) {
            maxBytes = 1024L * 1024;
        }
        if (maxRenderBytes <= 0) {
            maxRenderBytes = 5L * 1024 * 1024;
        }
        if (maxTextBytes <= 0) {
            maxTextBytes = 1024L * 1024;
        }
        if (splitDirs == null || splitDirs.isEmpty()) {
            splitDirs = DEFAULT_SPLIT_DIRS;
        } else {
            splitDirs = splitDirs.stream()
                    .filter(d -> d != null && !d.isBlank())
                    .map(String::toLowerCase)
                    .toList();
            if (splitDirs.isEmpty()) {
                splitDirs = DEFAULT_SPLIT_DIRS;
            }
        }
        if (maxStatsRows <= 0) {
            maxStatsRows = 10_000;
        }
        if (maxStatsFiles <= 0) {
            maxStatsFiles = 200;
        }
        if (maxStatsBytes <= 0) {
            maxStatsBytes = 64L * 1024 * 1024;
        }
    }
}
