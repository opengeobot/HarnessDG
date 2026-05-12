/**
 * 功能：请求上下文过滤器，从请求头提取 Trace ID、IP 地址和 User-Agent 并注入上下文
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.common.log;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_HEADER = "X-Trace-Id";
    private static final String MDC_TRACE_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Trace ID
        String traceId = request.getHeader(TRACE_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = TraceContext.generateTraceId();
        }
        TraceContext.setTraceId(traceId);
        MDC.put(MDC_TRACE_KEY, traceId);
        response.setHeader(TRACE_HEADER, traceId);

        // IP 地址（优先取 X-Forwarded-For，其次取 RemoteAddr）
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isBlank()) {
            ipAddress = request.getRemoteAddr();
        } else {
            // X-Forwarded-For 可能包含多个 IP，取第一个
            ipAddress = ipAddress.split(",")[0].trim();
        }
        TraceContext.setIpAddress(ipAddress);

        // User-Agent
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null && !userAgent.isBlank()) {
            TraceContext.setUserAgent(userAgent);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TraceContext.clear();
            MDC.remove(MDC_TRACE_KEY);
        }
    }
}
