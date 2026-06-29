/*
 * 功能: 创建资产 REST 请求体。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.api;

import com.aihub.asset.domain.AssetType;
import com.aihub.asset.domain.Visibility;
import java.util.List;

/**
 * 创建资产请求体。
 *
 * @param type        资产类型（MODEL/DATASET）
 * @param namespace   命名空间
 * @param name        名称
 * @param displayName 展示名称
 * @param description 描述
 * @param visibility  可见性
 * @param owners      Owner 列表
 * @param tags        标签列表
 * @param license     许可证
 * @param model       模型画像（模型类有效）
 * @param dataset     数据集画像（数据集类有效）
 */
public record CreateAssetRequest(AssetType type,
                                 String namespace,
                                 String name,
                                 String displayName,
                                 String description,
                                 Visibility visibility,
                                 List<String> owners,
                                 List<String> tags,
                                 String license,
                                 ModelInput model,
                                 DatasetInput dataset) {

    /** 模型画像输入。 */
    public record ModelInput(String framework, String task, String architecture) {
    }

    /** 数据集画像输入。 */
    public record DatasetInput(String format, String modality) {
    }
}
