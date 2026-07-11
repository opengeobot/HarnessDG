/*
 * 功能: 强制改密拦截器——must_change_password 用户除认证与改密端点外，访问受保护资源返回 403。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.api;

import com.aihub.identity.application.PasswordChangeQueryPort;
import com.aihub.shared.error.AuthorizationException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.aihub.shared.identity.PrincipalType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 强制改密拦截器。
 *
 * <p>当前已认证用户若处于 {@code must_change_password} 状态，除 {@code /api/v1/auth/**}、{@code /api/v1/me}
 * 与 {@code /api/v1/me/password} 外的受保护端点一律返回 {@link ErrorCode#PASSWORD_CHANGE_REQUIRED} 403，
 * 引导其先完成改密。匿名端点不命中（无主体）。
 */
public class PasswordChangeRequiredInterceptor implements HandlerInterceptor {

    private final PasswordChangeQueryPort passwordChangeQueryPort;

    public PasswordChangeRequiredInterceptor(PasswordChangeQueryPort passwordChangeQueryPort) {
        this.passwordChangeQueryPort = passwordChangeQueryPort;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        PrincipalContext context = PrincipalContextHolder.current().orElse(null);
        if (context == null || context.principalId() == null || context.principalType() != PrincipalType.USER) {
            return true;
        }
        if (isExempt(request)) {
            return true;
        }
        boolean mustChange = passwordChangeQueryPort.isPasswordChangeRequired(context.principalId());
        if (mustChange) {
            throw new AuthorizationException(
                    ErrorCode.PASSWORD_CHANGE_REQUIRED, "password change required", Map.of());
        }
        return true;
    }

    private boolean isExempt(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/")
                || path.equals("/api/v1/me")
                || path.equals("/api/v1/me/password");
    }
}
