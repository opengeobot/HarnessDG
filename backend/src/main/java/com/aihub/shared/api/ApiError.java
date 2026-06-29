/*
 * 功能: 统一失败响应结构，承载语义错误码、可重试标识与追踪上下文。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.shared.api;

import java.util.Map;

/**
 * 统一失败响应结构。
 *
 * <p>对外只暴露语义化 {@code code}、可读 {@code message}、国际化键 {@code i18nKey}、
 * 结构化 {@code details}、是否可重试 {@code retryable} 以及追踪上下文；
 * 禁止向客户端泄露堆栈、SQL、Bucket 或内部 URL。
 *
 * @param code      语义错误码
 * @param message   面向开发者的可读信息
 * @param i18nKey   前端国际化键
 * @param details   结构化补充信息（已脱敏）
 * @param retryable 是否可由调用方安全重试
 * @param requestId 请求 ID
 * @param traceId   分布式追踪 ID
 */
public record ApiError(String code,
                       String message,
                       String i18nKey,
                       Map<String, Object> details,
                       boolean retryable,
                       String requestId,
                       String traceId) {
}
