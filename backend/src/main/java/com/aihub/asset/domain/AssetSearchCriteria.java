/*
 * 功能: 资产检索条件，封装字段过滤、关键词、访问可见性与游标分页参数。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.domain;

import java.util.List;
import java.util.Set;

/**
 * 资产检索条件。
 *
 * <p>精确字段过滤 + 关键词全文 + 访问可见性过滤（在数据库阶段完成）。{@code allowedVisibilities}
 * 由应用层依据当前主体计算并下推到 SQL，确保不返回越权资产。游标分页避免深分页。
 *
 * @param keyword             关键词（匹配名称/展示名/描述/标签）
 * @param type                资产类型过滤（可空）
 * @param namespace           命名空间过滤（可空）
 * @param organizationId      组织 ID 过滤（可空，下推到 SQL）
 * @param framework           模型框架过滤（可空）
 * @param task                模型任务过滤（可空）
 * @param format              数据格式过滤（可空）
 * @param modality            数据模态过滤（可空）
 * @param tagId               受控标签 ID 过滤（可空，通过 asset_tag 关联表过滤）
 * @param owner               Owner 包含过滤（可空）
 * @param statuses            允许返回的状态集合（默认排除 ARCHIVED）
 * @param allowedVisibilities 当前主体可见的可见性集合（权限下推）
 * @param cursor              游标（上一页末项编码），首页为 {@code null}
 * @param limit               每页大小
 */
public record AssetSearchCriteria(String keyword,
                                  AssetType type,
                                  String namespace,
                                  String organizationId,
                                  String framework,
                                  String task,
                                  String format,
                                  String modality,
                                  String tagId,
                                  String owner,
                                  Set<AssetStatus> statuses,
                                  Set<Visibility> allowedVisibilities,
                                  String cursor,
                                  int limit) {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    public AssetSearchCriteria {
        statuses = statuses == null || statuses.isEmpty()
                ? Set.of(AssetStatus.ACTIVE, AssetStatus.DEPRECATED)
                : Set.copyOf(statuses);
        allowedVisibilities = allowedVisibilities == null || allowedVisibilities.isEmpty()
                ? Set.of(Visibility.PUBLIC)
                : Set.copyOf(allowedVisibilities);
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        } else if (limit > MAX_LIMIT) {
            limit = MAX_LIMIT;
        }
    }

    /**
     * @return 状态名称列表，便于仓储以参数化 SQL 下推
     */
    public List<String> statusNames() {
        return statuses.stream().map(Enum::name).toList();
    }

    /**
     * @return 可见性名称列表，便于仓储以参数化 SQL 下推
     */
    public List<String> visibilityNames() {
        return allowedVisibilities.stream().map(Enum::name).toList();
    }
}
