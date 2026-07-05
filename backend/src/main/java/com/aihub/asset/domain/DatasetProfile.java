/*
 * 功能: 数据集扩展画像，承载数据集类资产的领域字段。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.domain;

import java.util.List;

/**
 * 数据集扩展画像。
 *
 * <p>仅数据集类资产持有，对应 {@code asset_dataset} 表。字段可为空，便于登记时逐步完善。
 * V2 的 {@code format}/{@code modality} 保留兼容，新字段以 JSONB 多值编码为主。
 *
 * @param format          数据格式（字典 itemCode，V2 兼容）
 * @param modality        数据模态（字典 itemCode，V2 兼容）
 * @param taskCodes       任务编码列表（JSON 数组）
 * @param modalityCodes   模态编码列表（JSON 数组）
 * @param formatCodes     格式编码列表（JSON 数组）
 * @param languageCodes   语言编码列表（JSON 数组）
 * @param sensitivityCode 敏感级别编码
 * @param sampleCount     样本数量
 * @param totalBytes      数据总字节数
 * @param sizeBucketCode  大小区间编码
 */
public record DatasetProfile(String format,
                             String modality,
                             List<String> taskCodes,
                             List<String> modalityCodes,
                             List<String> formatCodes,
                             List<String> languageCodes,
                             String sensitivityCode,
                             Long sampleCount,
                             Long totalBytes,
                             String sizeBucketCode) {

    /** 兼容 P0-B 两参数构造。 */
    public DatasetProfile(String format, String modality) {
        this(format, modality, null, null, null, null, null, null, null, null);
    }

    /** 空画像，便于未提供数据集字段时占位。 */
    public static DatasetProfile empty() {
        return new DatasetProfile(null, null, null, null, null, null, null, null, null, null);
    }
}
