/*
 * 功能: 资产检索摘要投影，承载列表与检索所需的小体积字段。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.domain;

import java.time.Instant;
import java.util.List;

/**
 * 资产检索摘要投影。
 *
 * <p>面向列表/检索的小体积只读视图，文件清单与完整卡片按需另查（设计 8.3 节）。
 * 由仓储以显式 SQL 投影直接装配，避免"先查全量再过滤"。
 *
 * @param assetId        业务资产 ID
 * @param type           资产类型
 * @param namespace      命名空间
 * @param organizationId 组织 ID（治理作用域）
 * @param projectId      项目 ID（治理作用域）
 * @param name           名称
 * @param displayName    展示名称
 * @param description    描述
 * @param visibility     可见性
 * @param status         状态
 * @param owners         Owner 列表
 * @param tags           标签列表（legacy 自由标签，只读回显）
 * @param tagIds         受控标签 ID 列表（来自 asset_tag 关联）
 * @param license        许可证
 * @param framework      模型框架（仅模型）
 * @param task           模型任务（仅模型）
 * @param format         数据格式（仅数据集）
 * @param modality       数据模态（仅数据集）
 * @param updatedAt      更新时间
 * @param matchedFields  关键词检索命中的字段名（无关键词时为空）
 */
public record AssetSummary(String assetId,
                           AssetType type,
                           String namespace,
                           String organizationId,
                           String projectId,
                           String name,
                           String displayName,
                           String description,
                           Visibility visibility,
                           AssetStatus status,
                           List<String> owners,
                           List<String> tags,
                           List<String> tagIds,
                           String license,
                           String framework,
                           String task,
                           String format,
                           String modality,
                           Instant updatedAt,
                           List<String> matchedFields) {
}
