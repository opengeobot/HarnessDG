/*
 * 功能: organization 模块 api 子包说明。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */

/**
 * organization 模块（组织、项目、成员）的 api 适配层。
 *
 * <p>放置 REST 适配器（{@code OrganizationController}/{@code ProjectController}），只做协议转换、
 * 上下文建立与统一授权调用，禁止直接调用 Mapper。授权判定走 {@code AuthorizationService}（fail-closed）。
 */
package com.aihub.organization.api;
