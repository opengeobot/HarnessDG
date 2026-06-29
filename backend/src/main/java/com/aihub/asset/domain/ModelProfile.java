/*
 * 功能: 模型扩展画像，承载模型类资产的领域字段。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.domain;

/**
 * 模型扩展画像。
 *
 * <p>仅模型类资产持有，对应 {@code asset_model} 表。字段可为空，便于登记时逐步完善。
 *
 * @param framework    框架（字典 itemCode，如 pytorch）
 * @param task         任务类型（字典 itemCode，如 text-generation）
 * @param architecture 架构描述
 */
public record ModelProfile(String framework, String task, String architecture) {

    /** 空画像，便于未提供模型字段时占位。 */
    public static ModelProfile empty() {
        return new ModelProfile(null, null, null);
    }
}
