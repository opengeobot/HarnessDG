/*
 * 功能: 统一鉴权失败响应写出器，将 401/403 渲染为脱敏的 ApiError JSON。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.bootstrap.security;

import com.aihub.shared.api.ApiError;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.MediaType;

/**
 * 鉴权失败响应写出辅助。
 *
 * <p>{@link RestAuthenticationEntryPoint} 与 {@link RestAccessDeniedHandler} 复用本类，
 * 保证 401/403 与全局异常处理器返回同构的 {@link ApiError}，不泄露堆栈或内部细节。
 */
final class SecurityResponseWriter {

    /** 过滤器在 Token 校验失败时写入的错误码请求属性键。 */
    static final String ERROR_CODE_ATTRIBUTE = "com.aihub.security.errorCode";

    private final ObjectMapper objectMapper;

    SecurityResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void write(HttpServletRequest request, HttpServletResponse response, ErrorCode defaultCode)
            throws IOException {
        ErrorCode errorCode = resolveErrorCode(request, defaultCode);
        ApiError body = new ApiError(
                errorCode.name(),
                errorCode.name(),
                errorCode.defaultI18nKey(),
                Map.of(),
                errorCode.retryable(),
                requestId(),
                traceId());
        response.setStatus(errorCode.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }

    private ErrorCode resolveErrorCode(HttpServletRequest request, ErrorCode defaultCode) {
        Object attribute = request.getAttribute(ERROR_CODE_ATTRIBUTE);
        if (attribute instanceof ErrorCode resolved) {
            return resolved;
        }
        return defaultCode;
    }

    private String requestId() {
        return PrincipalContextHolder.current().map(PrincipalContext::requestId).orElse(null);
    }

    private String traceId() {
        return PrincipalContextHolder.current().map(PrincipalContext::traceId).orElse(null);
    }
}
