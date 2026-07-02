/*
 * 功能: taxonomy 分类状态枚举，对应字典/标签的 status 列（稳定状态，非可配置字典项）。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.taxonomy.domain;

/**
 * taxonomy 分类状态。
 *
 * <p>字典类型、字典项与受控标签共用的稳定状态枚举，以代码枚举 + 数据库 CHECK 约束承载。
 * 与 OpenAPI {@code TaxonomyStatus} 对齐。
 */
public enum TaxonomyStatus {

    /** 启用：可被新建引用。 */
    ACTIVE,

    /** 停用：保留并可回显，但不可用于新建引用。 */
    DISABLED
}
