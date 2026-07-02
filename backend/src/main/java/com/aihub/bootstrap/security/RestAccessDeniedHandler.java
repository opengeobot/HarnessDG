/*
 * 功能: 访问拒绝处理器，已认证但权限不足时返回统一 403 ApiError。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.bootstrap.security;

import com.aihub.shared.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * 访问拒绝处理器。
 *
 * <p>已认证主体访问无权限资源时，返回统一 {@link ErrorCode#AUTH_PERMISSION_DENIED} 403 响应。
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final SecurityResponseWriter responseWriter;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.responseWriter = new SecurityResponseWriter(objectMapper);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        responseWriter.write(request, response, ErrorCode.AUTH_PERMISSION_DENIED);
    }
}
