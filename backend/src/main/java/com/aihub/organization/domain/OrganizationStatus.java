/*
 * 功能: 组织状态枚举，对应 organization/project 表的 status 列（稳定工作流状态，非字典）。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.organization.domain;

/**
 * 组织/项目状态。
 *
 * <p>稳定工作流状态以代码枚举 + 数据库 CHECK 约束承载，不作为可配置字典项。
 */
public enum OrganizationStatus {

    /** 活跃。 */
    ACTIVE,

    /** 禁用。 */
    DISABLED
}
