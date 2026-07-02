/*
 * 功能: 未认证访问入口，命中受保护端点但缺失/无效凭据时返回统一 401 ApiError。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.bootstrap.security;

import com.aihub.shared.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 未认证访问入口。
 *
 * <p>fail-closed：受保护端点在缺失或无效凭据时由本入口返回统一 {@link ErrorCode#AUTH_UNAUTHENTICATED}
 * （或过滤器标记的 {@link ErrorCode#AUTH_TOKEN_EXPIRED}）401 响应，绝不放行匿名访问。
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityResponseWriter responseWriter;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.responseWriter = new SecurityResponseWriter(objectMapper);
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        responseWriter.write(request, response, ErrorCode.AUTH_UNAUTHENTICATED);
    }
}
