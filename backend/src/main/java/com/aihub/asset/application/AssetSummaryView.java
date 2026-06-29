/*
 * 功能: 资产检索摘要视图，用于列表/检索结果的小体积返回。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.application;

import com.aihub.asset.domain.AssetStatus;
import com.aihub.asset.domain.AssetSummary;
import com.aihub.asset.domain.AssetType;
import com.aihub.asset.domain.Visibility;
import java.time.Instant;
import java.util.List;

/**
 * 资产检索摘要视图。
 *
 * @param assetId     业务资产 ID
 * @param type        资产类型
 * @param namespace   命名空间
 * @param name        名称
 * @param displayName 展示名称
 * @param description 描述
 * @param visibility  可见性
 * @param status      状态
 * @param owners      Owner 列表
 * @param tags        标签列表
 * @param license     许可证
 * @param framework   模型框架（仅模型）
 * @param task        模型任务（仅模型）
 * @param format      数据格式（仅数据集）
 * @param modality    数据模态（仅数据集）
 * @param updatedAt   更新时间
 */
public record AssetSummaryView(String assetId,
                               AssetType type,
                               String namespace,
                               String name,
                               String displayName,
                               String description,
                               Visibility visibility,
                               AssetStatus status,
                               List<String> owners,
                               List<String> tags,
                               String license,
                               String framework,
                               String task,
                               String format,
                               String modality,
                               Instant updatedAt) {

    /**
     * 由领域摘要投影构造视图。
     *
     * @param summary 领域摘要
     * @return 摘要视图
     */
    public static AssetSummaryView from(AssetSummary summary) {
        return new AssetSummaryView(
                summary.assetId(),
                summary.type(),
                summary.namespace(),
                summary.name(),
                summary.displayName(),
                summary.description(),
                summary.visibility(),
                summary.status(),
                summary.owners(),
                summary.tags(),
                summary.license(),
                summary.framework(),
                summary.task(),
                summary.format(),
                summary.modality(),
                summary.updatedAt());
    }
}
