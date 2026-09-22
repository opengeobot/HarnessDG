/*
 * 功能: 认证 REST 适配器，提供登录、刷新、凭据交换、登出、当前主体与改密接口。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.api;

import com.aihub.identity.api.IdentityRequests.ChangePasswordRequest;
import com.aihub.identity.api.IdentityRequests.ClientCredentialTokenRequest;
import com.aihub.identity.api.IdentityRequests.LoginRequest;
import com.aihub.identity.api.IdentityRequests.RefreshTokenRequest;
import com.aihub.identity.api.IdentityResponses.CurrentPrincipalPayload;
import com.aihub.identity.api.IdentityResponses.TokenPairPayload;
import com.aihub.identity.application.AuthenticationApplicationService;
import com.aihub.identity.application.CurrentPrincipalView;
import com.aihub.identity.application.TokenPairResult;
import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.identity.PrincipalContext;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证 REST 适配器。
 *
 * <p>作为适配层仅做请求映射、Cookie 处理与上下文透传，业务规则在应用服务/领域内完成；不直接访问 Mapper。
 * 浏览器刷新 JWT 通过 Secure Cookie 下发，访问 JWT 仅在响应体返回。
 */
@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthenticationApplicationService authService;
    private final RefreshCookieFactory cookieFactory;

    public AuthController(AuthenticationApplicationService authService, RefreshCookieFactory cookieFactory) {
        this.authService = authService;
        this.cookieFactory = cookieFactory;
    }

    /**
     * 本地用户登录，签发令牌对；刷新 JWT 同时写入安全 Cookie。
     */
    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponse<TokenPairPayload>> login(@RequestBody LoginRequest request) {
        if (request == null || isBlank(request.username()) || isBlank(request.password())) {
            throw new ValidationException("username and password are required");
        }
        TokenPairResult result = authService.login(request.username(), request.password());
        // 登录通过浏览器场景下发 Cookie，响应体不再重复返回 refreshToken。
        return withRefreshCookie(result, IdentityApiContext.respond(TokenPairPayload.from(result, false)));
    }

    /**
     * 刷新令牌轮换：优先读取 Cookie，其次读取请求体的 refreshToken。
     */
    @PostMapping("/auth/refresh")
    public ResponseEntity<ApiResponse<TokenPairPayload>> refresh(
            @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletRequest httpRequest) {
        String fromCookie = readRefreshCookie(httpRequest);
        boolean fromBody = fromCookie == null && request != null && !isBlank(request.refreshToken());
        String refreshToken = fromCookie != null ? fromCookie
                : (request == null ? null : request.refreshToken());
        TokenPairResult result = authService.refresh(refreshToken);
        // 来自请求体的非浏览器客户端在响应体返回新 refresh；浏览器场景仅更新 Cookie。
        return withRefreshCookie(result, IdentityApiContext.respond(TokenPairPayload.from(result, fromBody)));
    }

    /**
     * Agent/Service/API Client 凭据交换 access JWT（默认不下发刷新 Cookie）。
     */
    @PostMapping("/auth/token")
    public ApiResponse<TokenPairPayload> exchangeToken(@RequestBody ClientCredentialTokenRequest request) {
        if (request == null || isBlank(request.subjectId()) || isBlank(request.credential())) {
            throw new ValidationException("subjectId and credential are required");
        }
        TokenPairResult result = authService.exchangeClientCredential(request.subjectId(), request.credential());
        return IdentityApiContext.respond(TokenPairPayload.from(result, false));
    }

    /**
     * Agent 凭据交换别名端点，行为与 {@link #exchangeToken} 相同。
     */
    @PostMapping("/auth/agent/token")
    public ApiResponse<TokenPairPayload> exchangeAgentToken(@RequestBody ClientCredentialTokenRequest request) {
        return exchangeToken(request);
    }

    /**
     * 登出：吊销当前刷新令牌族并清除 Cookie。
     */
    @PostMapping("/auth/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletRequest httpRequest) {
        String fromCookie = readRefreshCookie(httpRequest);
        String refreshToken = fromCookie != null ? fromCookie
                : (request == null ? null : request.refreshToken());
        authService.logout(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clear())
                .body(IdentityApiContext.respond(null));
    }

    /**
     * 获取当前主体概要。
     */
    @GetMapping("/me")
    public ApiResponse<CurrentPrincipalPayload> me() {
        PrincipalContext context = IdentityApiContext.require();
        CurrentPrincipalView view = authService.currentPrincipal(context.principalId(), context.principalType());
        return IdentityApiContext.respond(CurrentPrincipalPayload.from(view));
    }

    /**
     * 修改当前用户口令。
     */
    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(@RequestBody ChangePasswordRequest request) {
        if (request == null || isBlank(request.currentPassword()) || isBlank(request.newPassword())) {
            throw new ValidationException("currentPassword and newPassword are required");
        }
        PrincipalContext context = IdentityApiContext.require();
        authService.changePassword(context.principalId(), request.currentPassword(), request.newPassword());
        return IdentityApiContext.respond(null);
    }

    private ResponseEntity<ApiResponse<TokenPairPayload>> withRefreshCookie(
            TokenPairResult result, ApiResponse<TokenPairPayload> body) {
        if (result.refreshToken() == null) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        cookieFactory.build(result.refreshToken(), result.refreshExpiresIn()))
                .body(body);
    }

    private String readRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (cookieFactory.cookieName().equals(cookie.getName()) && !isBlank(cookie.getValue())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
