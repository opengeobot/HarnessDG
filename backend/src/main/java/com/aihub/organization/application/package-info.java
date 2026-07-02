/*
 * 功能: organization 模块 application 子包说明。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */

/**
 * organization 模块的 application 应用层，承载用例编排与统一授权调用。
 *
 * <p>编排组织/项目/成员用例，复用领域不变量与仓储端口；仅返回视图，不泄露持久化实体。
 * 通过出站端口 {@code OrganizationMembershipPort} 向 authorization 模块暴露成员作用域查询；
 * 本模块不反向依赖 authorization（避免循环依赖）。
 */
package com.aihub.organization.application;
