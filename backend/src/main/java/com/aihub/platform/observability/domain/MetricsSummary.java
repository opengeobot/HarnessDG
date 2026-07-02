/*
 * 功能: 平台指标摘要视图，聚合 JVM/DB/任务/依赖等运维指标供受限端点返回。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.platform.observability.domain;

import java.time.Instant;
import java.util.Map;

/**
 * 平台指标摘要视图。
 *
 * <p>对应 OpenAPI 契约 {@code MetricsSummary}：{@code generatedAt} 为生成时间戳，
 * {@code api}/{@code jobs}/{@code dependencies} 为各维度的指标快照（自由结构 Map）。
 * 仅暴露非敏感运维指标，不含凭据/内部 Endpoint。
 *
 * @param generatedAt  生成时间
 * @param api          API 维度指标（如 http_server_requests 计数）
 * @param jobs         任务维度指标（如 pending/running/dead 计数）
 * @param dependencies 依赖维度指标（如总体状态与各依赖健康）
 */
public record MetricsSummary(Instant generatedAt,
                             Map<String, Object> api,
                             Map<String, Object> jobs,
                             Map<String, Object> dependencies) {

    public MetricsSummary {
        api = api == null ? Map.of() : Map.copyOf(api);
        jobs = jobs == null ? Map.of() : Map.copyOf(jobs);
        dependencies = dependencies == null ? Map.of() : Map.copyOf(dependencies);
    }
}
