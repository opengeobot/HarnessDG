/*
 * 功能: 资产可见性稳定枚举，参与权限与检索过滤。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.domain;

/**
 * 资产可见性。
 *
 * <p>稳定领域枚举，与数据库 {@code ck_asset_visibility} 约束一致。可见性参与检索阶段的权限过滤，
 * 但不替代资源级授权判断。
 */
public enum Visibility {

    /** 私有：仅 Owner 与显式授权者可见。 */
    PRIVATE,

    /** 内部：组织内员工可见。 */
    INTERNAL,

    /** 公开：全平台可见。 */
    PUBLIC
}
