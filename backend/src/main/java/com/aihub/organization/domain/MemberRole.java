/*
 * 功能: 组织成员角色枚举，对应 organization_member.role 列（P0-B 简化为成员关系角色字符串）。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.organization.domain;

/**
 * 组织成员角色。
 *
 * <p>P0-B 简化为成员关系角色字符串；后续若接入完整角色体系，可由授权模块的角色绑定覆盖。
 */
public enum MemberRole {

    /** 所有者。 */
    OWNER,

    /** 普通成员。 */
    MEMBER
}
