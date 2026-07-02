/*
 * 功能: 受控标签作用域类型枚举，对应 system_tag.scope_type 列（PLATFORM/ORGANIZATION）。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.taxonomy.tag.domain;

/**
 * 受控标签作用域类型。
 *
 * <p>与 OpenAPI {@code TagScopeType} 对齐。平台标签全平台可见；组织标签仅在该组织作用域内可引用。
 */
public enum TagScopeType {

    /** 平台作用域。 */
    PLATFORM,

    /** 组织作用域。 */
    ORGANIZATION
}
