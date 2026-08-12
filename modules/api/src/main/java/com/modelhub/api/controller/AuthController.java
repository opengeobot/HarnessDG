package com.modelhub.api.controller;

import com.modelhub.api.dto.AuthRequests.ChangePasswordRequest;
import com.modelhub.api.dto.AuthRequests.LoginRequest;
import com.modelhub.api.dto.AuthRequests.RegisterRequest;
import com.modelhub.api.dto.AuthRequests.UpdateProfileRequest;
import com.modelhub.api.support.Principals;
import com.modelhub.api.web.SessionCookies;
import com.modelhub.identity.config.IdentityProperties;
import com.modelhub.identity.security.CurrentPrincipal;
import com.modelhub.identity.service.AuthService;
import com.modelhub.identity.service.AuthService.AuthResult;
import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.error.ErrorCode;
import com.modelhub.shared.web.ApiEnvelope;
import com.modelhub.shared.web.ETags;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 认证端点（04 §3）：register/login/refresh/logout/logout-all/me/change-password。
 * refresh/logout 要求 mh_refresh Cookie + X-CSRF-Token + Origin 三匹配（02 §6.1）。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    /** 认证响应 data：accessToken/expiresIn/user/csrfToken（04 §3）。 */
    public record AuthData(String accessToken, long expiresIn, AuthService.UserView user, String csrfToken) {}

    private final AuthService authService;
    private final IdentityProperties props;

    public AuthController(AuthService authService, IdentityProperties props) {
        this.authService = authService;
        this.props = props;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiEnvelope<AuthData>> register(@Valid @RequestBody RegisterRequest body,
                                                          HttpServletRequest request,
                                                          HttpServletResponse response) {
        AuthResult result = authService.register(body.username(), body.password(), body.nickname(),
                clientIp(request), request.getHeader("User-Agent"));
        issueCookies(response, result, request.isSecure());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.created(toData(result)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiEnvelope<AuthData>> login(@Valid @RequestBody LoginRequest body,
                                                       HttpServletRequest request,
                                                       HttpServletResponse response) {
        AuthResult result = authService.login(body.username(), body.password(),
                clientIp(request), request.getHeader("User-Agent"));
        issueCookies(response, result, request.isSecure());
        return ResponseEntity.ok(ApiEnvelope.ok(toData(result)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiEnvelope<AuthData>> refresh(HttpServletRequest request,
                                                         HttpServletResponse response) {
        String refreshToken = requireRefreshCookie(request);
        String csrfToken = request.getHeader("X-CSRF-Token");
        AuthResult result = authService.refresh(refreshToken, csrfToken,
                request.getHeader("Origin"), clientIp(request));
        issueCookies(response, result, request.isSecure());
        return ResponseEntity.ok(ApiEnvelope.ok(toData(result)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = requireRefreshCookie(request);
        authService.logout(refreshToken, request.getHeader("X-CSRF-Token"), request.getHeader("Origin"));
        SessionCookies.clear(response, request.isSecure());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(HttpServletRequest request, HttpServletResponse response) {
        authService.logoutAll(Principals.requireCurrent(request));
        SessionCookies.clear(response, request.isSecure());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<ApiEnvelope<AuthService.UserView>> me(HttpServletRequest request) {
        CurrentPrincipal principal = Principals.requireCurrent(request);
        AuthService.UserView view = authService.getMe(principal);
        return ResponseEntity.ok()
                .eTag(ETags.ofVersion(view.profileVersion()))
                .body(ApiEnvelope.ok(view));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiEnvelope<AuthService.UserView>> updateMe(
            @Valid @RequestBody UpdateProfileRequest body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        CurrentPrincipal principal = Principals.requireCurrent(request);
        AuthService.UserView view = authService.updateProfile(principal, body.nickname(), ifMatch);
        return ResponseEntity.ok()
                .eTag(ETags.ofVersion(view.profileVersion()))
                .body(ApiEnvelope.ok(view));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest body,
                                               HttpServletRequest request,
                                               HttpServletResponse response) {
        authService.changePassword(Principals.requireCurrent(request),
                body.currentPassword(), body.newPassword());
        SessionCookies.clear(response, request.isSecure());
        return ResponseEntity.noContent().build();
    }

    private void issueCookies(HttpServletResponse response, AuthResult result, boolean secure) {
        long maxAge = Duration.between(java.time.OffsetDateTime.now(), result.session().expiresAt()).getSeconds();
        SessionCookies.issue(response, result.session().refreshToken(), result.session().csrfToken(),
                Math.max(0, maxAge), secure);
    }

    private AuthData toData(AuthResult result) {
        long expiresIn = props.jwt().accessTokenTtlSeconds();
        return new AuthData(result.session().accessToken(), expiresIn, result.user(),
                result.session().csrfToken());
    }

    private static String requireRefreshCookie(HttpServletRequest request) {
        String token = SessionCookies.readRefreshCookie(request);
        if (token == null || token.isBlank()) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "缺少 mh_refresh Cookie");
        }
        return token;
    }

    private String clientIp(HttpServletRequest request) {
        String header = props.clientIpHeader();
        if (header != null && !header.isBlank()) {
            String forwarded = request.getHeader(header);
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
