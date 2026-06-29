/*
 * 功能: 资产类型稳定枚举（模型/数据集），直接影响领域分支与数据库约束。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.domain;

/**
 * 资产类型。
 *
 * <p>稳定领域枚举，与数据库 {@code ck_asset_type} 约束一一对应，不在运行时动态扩展。
 */
public enum AssetType {

    /** 模型。 */
    MODEL,

    /** 数据集。 */
    DATASET
}
