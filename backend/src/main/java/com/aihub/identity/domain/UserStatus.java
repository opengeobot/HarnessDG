/*
 * 功能: 本地用户状态枚举（稳定流程状态，代码 Enum + 数据库 CHECK 约束）。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.domain;

/**
 * 本地用户状态。
 *
 * <p>稳定流程状态由代码枚举与数据库约束共同保证，不纳入动态字典。
 */
public enum UserStatus {

    /** 待激活（如管理员重置口令后，等待首次登录修改）。 */
    PENDING_ACTIVATION,

    /** 活跃可用。 */
    ACTIVE,

    /** 因连续登录失败被临时锁定。 */
    LOCKED,

    /** 已禁用，凭据立即失效。 */
    DISABLED
}
