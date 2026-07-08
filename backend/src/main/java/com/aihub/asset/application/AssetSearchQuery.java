/*
 * 功能: 资产检索查询输入，应用层据此结合主体权限构造领域检索条件。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.application;

import com.aihub.asset.domain.AssetType;
import java.util.List;

/**
 * 资产检索查询输入。
 *
 * @param keyword         关键词
 * @param type            类型过滤（可空）
 * @param namespace       命名空间过滤（可空）
 * @param organizationId  组织 ID 过滤（可空，治理作用域下推）
 * @param projectId       项目 ID 过滤（可空）
 * @param visibility      可见性过滤（可空）
 * @param status          状态过滤（可空）
 * @param teamId          团队 ID 过滤（可空，匹配 ownerTeamId）
 * @param framework       模型框架过滤（可空）
 * @param task            模型任务过滤（可空）
 * @param format          数据格式过滤（可空）
 * @param modality        数据模态过滤（可空）
 * @param tagId           受控标签 ID 过滤（可空，通过 asset_tag 关联表过滤）
 * @param owner           Owner 过滤（可空）
 * @param language        语言过滤（可空）
 * @param sensitivity     敏感等级过滤（可空）
 * @param taskCodes       多值模型任务过滤（可空，DATASET 多值分类）
 * @param modalityCodes   多值数据模态过滤（可空，DATASET 多值分类）
 * @param formatCodes     多值数据格式过滤（可空，DATASET 多值分类）
 * @param includeArchived 是否包含归档资产（默认否，需管理员）
 * @param cursor          游标（首页为空）
 * @param limit           每页大小（&lt;=0 使用默认）
 * @param principalId     当前主体 ID（可空，P1 未接入认证）
 */
public record AssetSearchQuery(String keyword,
                               AssetType type,
                               String namespace,
                               String organizationId,
                               String projectId,
                               String visibility,
                               String status,
                               String teamId,
                               String framework,
                               String task,
                               String format,
                               String modality,
                               String tagId,
                               String owner,
                               String language,
                               String sensitivity,
                               List<String> taskCodes,
                               List<String> modalityCodes,
                               List<String> formatCodes,
                               boolean includeArchived,
                               String cursor,
                               int limit,
                               String principalId) {
}
