/*
 * 功能: HTTP 入口请求上下文过滤器，统一建立并清理 PrincipalContext。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.bootstrap;

import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * HTTP 入口请求上下文过滤器。
 *
 * <p>作为入口适配器统一建立请求关联上下文：接收或生成 {@code X-Request-Id}，从 {@code traceparent}
 * 提取或生成 traceId，并解析 {@code Accept-Language}。设计要求所有入口（Controller/MCP/Worker）
 * 统一建立 {@link PrincipalContext}，业务代码只读取不解析底层凭据。
 *
 * <p>P0 阶段尚未接入身份认证，本过滤器仅承载请求关联信息（requestId/traceId/locale），
 * 主体身份字段留空；真实主体解析在 P3 身份与权限阶段由认证适配器填充。
 * 过滤器最先执行并在请求结束后清理 ThreadLocal，避免线程复用导致上下文串台。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PrincipalContextFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACEPARENT_HEADER = "traceparent";
    public static final String ACCEPT_LANGUAGE_HEADER = "Accept-Language";

    private static final String DEFAULT_LOCALE = "zh-CN";
    private static final int TRACEPARENT_TRACE_ID_INDEX = 1;
    private static final int TRACE_ID_HEX_LENGTH = 32;

    private final IdGenerator idGenerator;

    public PrincipalContextFilter(IdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        String traceId = resolveTraceId(request);
        String locale = resolveLocale(request);

        PrincipalContextHolder.set(new PrincipalContext(
                null,
                null,
                null,
                null,
                List.of(),
                Set.of(),
                Set.of(),
                0,
                locale,
                requestId,
                traceId));

        // 回显关联标识，便于客户端与网关串联日志与 Trace。
        response.setHeader(REQUEST_ID_HEADER, requestId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            PrincipalContextHolder.clear();
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String headerValue = request.getHeader(REQUEST_ID_HEADER);
        if (StringUtils.hasText(headerValue)) {
            return headerValue.trim();
        }
        return idGenerator.generate(IdPrefix.REQUEST);
    }

    /**
     * 从 W3C {@code traceparent} 头提取 trace-id（第二段，32 位十六进制）；缺失或非法时生成新的 trace-id。
     */
    private String resolveTraceId(HttpServletRequest request) {
        String traceparent = request.getHeader(TRACEPARENT_HEADER);
        if (StringUtils.hasText(traceparent)) {
            String[] segments = traceparent.trim().split("-");
            if (segments.length > TRACEPARENT_TRACE_ID_INDEX) {
                String candidate = segments[TRACEPARENT_TRACE_ID_INDEX];
                if (isValidTraceId(candidate)) {
                    return candidate;
                }
            }
        }
        return generateTraceId();
    }

    private boolean isValidTraceId(String candidate) {
        if (candidate.length() != TRACE_ID_HEX_LENGTH) {
            return false;
        }
        for (int index = 0; index < candidate.length(); index++) {
            char character = candidate.charAt(index);
            boolean hex = (character >= '0' && character <= '9')
                    || (character >= 'a' && character <= 'f');
            if (!hex) {
                return false;
            }
        }
        // 全零 trace-id 在 W3C 规范中无效。
        return !candidate.chars().allMatch(value -> value == '0');
    }

    private String generateTraceId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 解析 {@code Accept-Language} 首个语言标签；缺失时回退默认语言。
     */
    private String resolveLocale(HttpServletRequest request) {
        String header = request.getHeader(ACCEPT_LANGUAGE_HEADER);
        if (!StringUtils.hasText(header)) {
            return DEFAULT_LOCALE;
        }
        String firstTag = header.split(",")[0].split(";")[0].trim();
        return StringUtils.hasText(firstTag) ? firstTag : DEFAULT_LOCALE;
    }
}
