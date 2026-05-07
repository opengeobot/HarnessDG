/**
 * 功能：Trace ID 上下文持有器，基于虚拟线程安全的 ThreadLocal
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.common.log;

import java.util.UUID;

public final class TraceContext {

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    private TraceContext() {}

    public static String getTraceId() {
        return TRACE_ID.get();
    }

    public static void setTraceId(String traceId) {
        TRACE_ID.set(traceId);
    }

    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public static void clear() {
        TRACE_ID.remove();
    }
}
