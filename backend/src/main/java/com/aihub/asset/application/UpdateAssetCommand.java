/*
 * 功能: 更新资产命令，承载可变元数据与操作者。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.application;

import com.aihub.asset.domain.DatasetProfile;
import com.aihub.asset.domain.ModelProfile;
import com.aihub.asset.domain.Visibility;
import java.util.List;

/**
 * 更新资产命令。坐标（namespace/type/name）不可变，仅修改可变元数据。
 *
 * @param expectedVersion 乐观锁版本号（必填，与资产当前 rowVersion 比对）
 * @param organizationId 所属组织 ID
 * @param projectId      所属项目 ID
 * @param displayName    展示名称
 * @param description 描述
 * @param visibility  可见性（为空表示不变更）
 * @param tagIds      受控标签 ID 列表（为空表示不变更；非空时重写 asset_tag 关联）
 * @param license     许可证
 * @param ownerTeamId 主 Owner 团队 ID（为空表示不变更）
 * @param model       模型画像（仅模型类有效，可空）
 * @param dataset     数据集画像（仅数据集类有效，可空）
 * @param principalId 操作者主体 ID（可空，P1 未接入认证）
 */
public record UpdateAssetCommand(long expectedVersion,
                                 String organizationId,
                                 String projectId,
                                 String displayName,
                                 String description,
                                 Visibility visibility,
                                 List<String> tagIds,
                                 String license,
                                 String ownerTeamId,
                                 ModelProfile model,
                                 DatasetProfile dataset,
                                 String principalId) {
}
