/*
 * 功能: 预览 Worker 资源隔离配置（行数/字节上限）。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.version.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 预览生成资源上限（前缀 {@code aihub.preview}）。
 *
 * <p>Worker 进程级内存/CPU 隔离由容器 cgroup 或 JVM 堆限制承担；本配置约束单次预览内容解析上限。
 *
 * @param maxRows  最大预览行数
 * @param maxCols  最大预览列数
 * @param maxBytes 预览内容最大字节数
 */
@ConfigurationProperties(prefix = "aihub.preview")
public record PreviewProperties(int maxRows, int maxCols, long maxBytes) {

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
    }
}
