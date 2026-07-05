/*
 * 功能: 幂等拦截器——对携带 Idempotency-Key 的请求透明复用首次结果。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
package com.aihub.shared.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 幂等拦截器。
 *
 * <p>对携带 {@code Idempotency-Key} 请求头的写请求，在 {@code preHandle} 中查询
 * {@link IdempotencyStore}：命中已完成记录时直接返回首次响应，不进入 Controller。
 * 未命中时放行，由 Controller 中的 {@code IdempotencyService.execute()} 完成首次执行和落库。
 *
 * <p>同键不同请求体（fingerprint 不匹配）返回 409 CONFLICT。
 */
@Component
public class IdempotencyInterceptor implements HandlerInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(IdempotencyInterceptor.class);
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final IdempotencyStore store;
    private final ObjectMapper objectMapper;

    public IdempotencyInterceptor(IdempotencyStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws IOException {
        String keyValue = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (keyValue == null || keyValue.isBlank()) {
            return true; // 无幂等键，正常放行
        }

        String principalId = resolvePrincipalId(request);
        IdempotencyKey key = new IdempotencyKey(keyValue, principalId,
                request.getMethod(), request.getRequestURI());

        Optional<IdempotencyRecord> existing = store.find(key);
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            LOG.debug("idempotency hit: key={}, status={}", keyValue, record.responseStatus());
            response.setStatus(record.responseStatus());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("X-Idempotency-Replayed", "true");
            if (record.responseBody() != null && !record.responseBody().isEmpty()) {
                response.getWriter().write(record.responseBody());
            }
            return false; // 已返回缓存响应，不进入 Controller
        }

        return true; // 未命中，放行进入 Controller
    }

    /**
     * 从请求属性中解析 principalId（由 JwtAuthenticationFilter 设置）。
     */
    private String resolvePrincipalId(HttpServletRequest request) {
        Object principal = request.getAttribute("principalId");
        if (principal instanceof String pid && !pid.isBlank()) {
            return pid;
        }
        // 回退到认证对象
        if (request.getUserPrincipal() != null) {
            return request.getUserPrincipal().getName();
        }
        return "anonymous";
    }
}
