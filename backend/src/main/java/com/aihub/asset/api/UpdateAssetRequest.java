/*
 * 功能: 更新资产 REST 请求体。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.api;

import com.aihub.asset.domain.Visibility;
import java.util.List;

/**
 * 更新资产请求体。坐标不可变，仅修改可变元数据。
 *
 * @param expectedVersion 乐观锁版本号（必填，与资产当前 rowVersion 比对）
 * @param organizationId 所属组织 ID（为空表示不变更）
 * @param projectId      所属项目 ID（为空表示不变更）
 * @param displayName    展示名称
 * @param description    描述
 * @param visibility     可见性（为空表示不变更）
 * @param owners         Owner 列表（为空表示不变更）
 * @param tags           标签列表（legacy，为空表示不变更）
 * @param tagIds         受控标签 ID 列表（为空表示不变更；非空时重写 asset_tag 关联）
 * @param license        许可证
 * @param ownerTeamId    主 Owner 团队 ID（为空表示不变更）
 * @param model          模型画像（模型类有效）
 * @param dataset        数据集画像（数据集类有效）
 */
public record UpdateAssetRequest(long expectedVersion,
                                 String organizationId,
                                 String projectId,
                                 String displayName,
                                 String description,
                                 Visibility visibility,
                                 List<String> owners,
                                 List<String> tags,
                                 List<String> tagIds,
                                 String license,
                                 String ownerTeamId,
                                 CreateAssetRequest.ModelInput model,
                                 CreateAssetRequest.DatasetInput dataset) {
}
