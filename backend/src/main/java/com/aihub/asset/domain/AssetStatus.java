/*
 * 功能: 资产目录条目状态稳定枚举。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.domain;

/**
 * 资产目录条目状态。
 *
 * <p>稳定领域枚举，与数据库 {@code ck_asset_status} 约束一致。注意：本状态描述资产目录条目的生命周期，
 * 与版本发布状态机（{@code VersionStatus}）相互独立。
 */
public enum AssetStatus {

    /** 活跃：正常登记并可被检索。 */
    ACTIVE,

    /** 弃用：仍可访问，但默认检索降权。 */
    DEPRECATED,

    /** 归档：默认不返回，仅管理员可见恢复。 */
    ARCHIVED
}
