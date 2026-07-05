/*
 * 功能: 模型扩展画像，承载模型类资产的领域字段。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.domain;

import java.util.List;

/**
 * 模型扩展画像。
 *
 * <p>仅模型类资产持有，对应 {@code asset_model} 表。字段可为空，便于登记时逐步完善。
 *
 * @param framework         框架（字典 itemCode，如 pytorch）
 * @param task              任务类型（字典 itemCode，如 text-generation）
 * @param architecture      架构描述
 * @param parameterScale    参数规模（如 7B/13B/70B）
 * @param precision         精度（如 fp16/bf16/int8）
 * @param weightFormat      权重格式（如 safetensors/gguf/bin）
 * @param runtime           运行时（如 onnx/vllm/triton）
 * @param knownRisks        已知风险
 * @param usageRestrictions 使用限制
 * @param sensitivityCode   敏感级别编码
 */
public record ModelProfile(String framework,
                           String task,
                           String architecture,
                           String parameterScale,
                           String precision,
                           String weightFormat,
                           String runtime,
                           List<String> knownRisks,
                           List<String> usageRestrictions,
                           String sensitivityCode) {

    /** 兼容 P0-B 三参数构造。 */
    public ModelProfile(String framework, String task, String architecture) {
        this(framework, task, architecture, null, null, null, null, null, null, null);
    }

    /** 空画像，便于未提供模型字段时占位。 */
    public static ModelProfile empty() {
        return new ModelProfile(null, null, null, null, null, null, null, null, null, null);
    }
}
