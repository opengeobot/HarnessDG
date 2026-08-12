package com.modelhub.shared.web;

import org.slf4j.MDC;

/**
 * traceId 上下文：写入 MDC，供 envelope 与日志使用；由 TraceFilter 负责生成与清理。
 */
public final class TraceContext {

    public static final String MDC_KEY = "traceId";

    private TraceContext() {}

    public static void set(String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            MDC.put(MDC_KEY, traceId);
        }
    }

    public static String currentTraceId() {
        String v = MDC.get(MDC_KEY);
        return v == null ? "-" : v;
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
