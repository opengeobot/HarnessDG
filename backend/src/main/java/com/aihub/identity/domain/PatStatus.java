/*
 * 功能: 个人访问令牌（PAT）生命周期状态枚举。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.identity.domain;

/**
 * PAT 状态。
 */
public enum PatStatus {

    /** 有效。 */
    ACTIVE,

    /** 已吊销。 */
    REVOKED,

    /** 已过期。 */
    EXPIRED
}
