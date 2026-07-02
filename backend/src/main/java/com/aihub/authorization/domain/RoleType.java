/*
 * 功能: 角色类型枚举，对应契约 RoleView.roleType（SYSTEM 内置 / CUSTOM 自定义）。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.authorization.domain;

/**
 * 角色类型。
 *
 * <p>{@code SYSTEM} 为系统内置角色（{@code builtin=1}，不可改名删除）；{@code CUSTOM} 为运营创建的自定义角色。
 */
public enum RoleType {

    /** 系统内置角色。 */
    SYSTEM,

    /** 自定义角色。 */
    CUSTOM
}
