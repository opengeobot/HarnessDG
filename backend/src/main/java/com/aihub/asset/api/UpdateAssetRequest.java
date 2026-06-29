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
 * @param displayName 展示名称
 * @param description 描述
 * @param visibility  可见性（为空表示不变更）
 * @param owners      Owner 列表（为空表示不变更）
 * @param tags        标签列表（为空表示不变更）
 * @param license     许可证
 * @param model       模型画像（模型类有效）
 * @param dataset     数据集画像（数据集类有效）
 */
public record UpdateAssetRequest(String displayName,
                                 String description,
                                 Visibility visibility,
                                 List<String> owners,
                                 List<String> tags,
                                 String license,
                                 CreateAssetRequest.ModelInput model,
                                 CreateAssetRequest.DatasetInput dataset) {
}
