/*
 * 功能: 结构化日志 MDC 字段键常量，统一公共日志字段命名。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.shared.logging;

/**
 * MDC 字段键常量。
 *
 * <p>公共字段由入口过滤器与 TaskDecorator 自动注入，业务代码不得手工拼接日志前缀。
 */
public final class LogFields {

    /** 请求 ID。 */
    public static final String REQUEST_ID = "requestId";

    /** 分布式追踪 ID。 */
    public static final String TRACE_ID = "traceId";

    /** 主体 ID。 */
    public static final String PRINCIPAL_ID = "principalId";

    /** 主体类型。 */
    public static final String PRINCIPAL_TYPE = "principalType";

    private LogFields() {
    }
}
