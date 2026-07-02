/*
 * 功能: shared.logging 包说明——结构化日志 MDC 字段与字段级脱敏契约。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */

/**
 * 结构化日志与脱敏支持。
 *
 * <p>提供统一 MDC 字段键 {@link com.aihub.shared.logging.LogFields} 与字段级脱敏器
 * {@link com.aihub.shared.logging.SensitiveDataMasker}，在日志边界统一清除认证头、Cookie、
 * 口令、JWT、预签名查询串与对象存储凭据，避免敏感信息进入运行日志。
 */
package com.aihub.shared.logging;
