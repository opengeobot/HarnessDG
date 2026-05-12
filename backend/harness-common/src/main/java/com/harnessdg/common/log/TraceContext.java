/**
 * 功能：请求上下文持有器（Trace ID + IP + UserAgent），基于虚拟线程安全的 ThreadLocal
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.common.log;

import java.util.UUID;

public final class TraceContext {

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> IP_ADDRESS = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_AGENT = new ThreadLocal<>();

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

    public static String getIpAddress() {
        return IP_ADDRESS.get();
    }

    public static void setIpAddress(String ipAddress) {
        IP_ADDRESS.set(ipAddress);
    }

    public static String getUserAgent() {
        return USER_AGENT.get();
    }

    public static void setUserAgent(String userAgent) {
        USER_AGENT.set(userAgent);
    }

    public static void clear() {
        TRACE_ID.remove();
        IP_ADDRESS.remove();
        USER_AGENT.remove();
    }
}
