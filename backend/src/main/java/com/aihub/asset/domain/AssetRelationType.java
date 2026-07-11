/*
 * 功能: 资产血缘关系类型枚举。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.asset.domain;

/**
 * 资产血缘关系类型，与 {@code asset_relation.relation_type} CHECK 约束对齐。
 */
public enum AssetRelationType {

    DERIVED_FROM,
    TRAINED_ON,
    BASED_ON,
    FINE_TUNED_FROM
}
