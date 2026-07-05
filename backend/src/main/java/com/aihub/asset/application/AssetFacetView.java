/*
 * 功能: 资产 Facet 视图，按维度统计资产数量，供前端高级筛选面板渲染。
 * 时间: 2026-07-04
 * 作者: AxeXie
 */
package com.aihub.asset.application;

import java.util.List;
import java.util.Map;

/**
 * 资产 Facet 视图。
 *
 * <p>每个维度返回 {@code (值 → 计数)} 映射，计数经访问权限过滤，不泄露无权资产。
 *
 * @param types       类型维度 (MODEL / DATASET → count)
 * @param frameworks  模型框架维度
 * @param tasks       模型任务维度
 * @param formats     数据集格式维度
 * @param modalities  数据集模态维度
 * @param licenses    许可证维度
 * @param totalCount  授权可见的资产总数
 */
public record AssetFacetView(Map<String, Long> types,
                             Map<String, Long> frameworks,
                             Map<String, Long> tasks,
                             Map<String, Long> formats,
                             Map<String, Long> modalities,
                             Map<String, Long> licenses,
                             long totalCount) {

    /**
     * 空 Facet（无数据时返回）。
     */
    public static AssetFacetView empty() {
        return new AssetFacetView(Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(), Map.of(), 0L);
    }
}
