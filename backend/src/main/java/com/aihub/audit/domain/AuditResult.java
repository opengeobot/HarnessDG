/*
 * 功能: 审计结果枚举，与 audit_log.result 约束及 OpenAPI AuditLogView.result 对齐。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.audit.domain;

/**
 * 审计结果。
 *
 * <p>取值与数据库 {@code ck_audit_log_result} 约束及 OpenAPI {@code AuditLogView.result} 严格对齐。
 */
public enum AuditResult {

    /** 成功。 */
    SUCCEEDED,

    /** 失败。 */
    FAILED,

    /** 被拒绝（如授权拒绝）。 */
    DENIED
}
