/*
 * 功能: 刷新令牌生命周期状态枚举（稳定流程状态，代码 Enum + 数据库 CHECK 约束）。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.domain;

/**
 * 刷新令牌（及 PAT 摘要）生命周期状态。
 */
public enum RefreshTokenStatus {

    /** 有效，可用于刷新。 */
    ACTIVE,

    /** 已轮换，被后继令牌取代；再次使用视为重放。 */
    ROTATED,

    /** 已吊销（登出、禁用、重放整族吊销）。 */
    REVOKED,

    /** 已自然过期。 */
    EXPIRED
}
