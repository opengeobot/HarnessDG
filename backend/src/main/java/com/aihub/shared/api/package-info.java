/*
 * 功能: shared.api 包说明——统一响应、错误响应与分页契约。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */

/**
 * 公共 API 契约：统一成功响应 {@link com.aihub.shared.api.ApiResponse}、
 * 失败响应 {@link com.aihub.shared.api.ApiError}、游标分页
 * {@link com.aihub.shared.api.CursorPage} 与偏移分页 {@link com.aihub.shared.api.PageResult}。
 *
 * <p>本包属于 shared-kernel，禁止依赖任何业务模块或基础设施 SDK。
 */
package com.aihub.shared.api;
