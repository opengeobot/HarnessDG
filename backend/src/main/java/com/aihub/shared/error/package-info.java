/*
 * 功能: shared.error 包说明——集中错误码与平台异常层次。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */

/**
 * 公共错误模型：集中错误码 {@link com.aihub.shared.error.ErrorCode} 与
 * 平台异常层次 {@link com.aihub.shared.error.PlatformException} 及其子类。
 *
 * <p>本包属于 shared-kernel，禁止依赖任何业务模块或基础设施 SDK。
 * 允许依赖 Spring Web 的 {@code HttpStatus} 作为状态码值对象。
 */
package com.aihub.shared.error;
