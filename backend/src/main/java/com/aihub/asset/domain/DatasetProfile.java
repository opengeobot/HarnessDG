/*
 * 功能: 数据集扩展画像，承载数据集类资产的领域字段。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.domain;

/**
 * 数据集扩展画像。
 *
 * <p>仅数据集类资产持有，对应 {@code asset_dataset} 表。字段可为空，便于登记时逐步完善。
 *
 * @param format   数据格式（字典 itemCode，如 parquet）
 * @param modality 数据模态（字典 itemCode，如 text/image）
 */
public record DatasetProfile(String format, String modality) {

    /** 空画像，便于未提供数据集字段时占位。 */
    public static DatasetProfile empty() {
        return new DatasetProfile(null, null);
    }
}
