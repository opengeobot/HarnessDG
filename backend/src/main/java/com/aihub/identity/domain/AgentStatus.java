/*
 * 功能: Agent 主体状态枚举（稳定流程状态，代码 Enum + 数据库 CHECK 约束）。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.domain;

/**
 * Agent 主体状态。
 */
public enum AgentStatus {

    /** 活跃可用。 */
    ACTIVE,

    /** 已禁用，凭据与 Token 立即失效。 */
    DISABLED
}
