package com.modelhub.identity.service;

import com.modelhub.identity.config.IdentityProperties;
import com.modelhub.identity.domain.NamespaceEntity;
import com.modelhub.identity.domain.UserEntity;
import com.modelhub.identity.repo.NamespaceRepository;
import com.modelhub.identity.repo.SysUserRoleRepository;
import com.modelhub.identity.repo.UserRepository;
import com.modelhub.identity.security.CurrentPrincipal;
import com.modelhub.identity.service.SessionService.IssuedSession;
import com.modelhub.identity.service.ratelimit.RateLimiter;
import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.error.ErrorCode;
import com.modelhub.shared.id.PublicIds;
import com.modelhub.shared.metrics.BusinessCounters;
import com.modelhub.shared.web.ETags;
import com.nimbusds.jwt.JWTClaimsSet;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 认证与账户服务（02 §6）：register/login/refresh/logout/logout-all/me/change-password。
 * 登录失败响应不区分用户不存在与密码错误；用户+IP 双维度限流。
 */
@Service
public class AuthService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9_-]{2,30}$");
    public static final String GENERIC_LOGIN_FAILURE = "用户名或密码错误";

    /** 当前用户视图（含 namespaceId 发现，03 §2.1）。字段名对齐 OpenAPI User schema。 */
    public record UserView(
            @JsonProperty("id") String publicId,
            String username, String nickname, String status,
            String namespaceId, List<String> platformRoles,
            @JsonProperty("version") long profileVersion,
            OffsetDateTime createdAt) {}

    /** 认证结果：user + access/refresh/csrf 凭据。 */
    public record AuthResult(UserView user, IssuedSession session) {}

    private final UserRepository users;
    private final NamespaceRepository namespaces;
    private final SysUserRoleRepository userRoles;
    private final SessionService sessionService;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final RateLimiter rateLimiter;
    private final PasswordEncoder passwordEncoder;
    private final IdentityProperties props;
    private final LoginGuard loginGuard;
    private final BusinessCounters counters;

    public AuthService(UserRepository users, NamespaceRepository namespaces,
                       SysUserRoleRepository userRoles, SessionService sessionService,
                       JwtService jwtService, AuditService auditService, RateLimiter rateLimiter,
                       PasswordEncoder passwordEncoder, IdentityProperties props, LoginGuard loginGuard,
                       BusinessCounters counters) {
        this.users = users;
        this.namespaces = namespaces;
        this.userRoles = userRoles;
        this.sessionService = sessionService;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.rateLimiter = rateLimiter;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
        this.loginGuard = loginGuard;
        this.counters = counters;
    }

    @Transactional
    public AuthResult register(String username, String password, String nickname, String ip, String userAgent) {
        enforceRateLimit("register:ip:" + ip);
        String normalized = normalizeUsername(username);
        if (users.existsByUsername(normalized) || namespaces.existsBySlug(normalized)) {
            throw new ApiException(ErrorCode.CONFLICT, "用户名已被占用");
        }
        UserEntity user = new UserEntity();
        user.setPublicId(PublicIds.next());
        user.setUsername(normalized);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setNickname(nickname == null || nickname.isBlank() ? normalized : nickname);
        users.save(user);

        NamespaceEntity ns = new NamespaceEntity();
        ns.setPublicId(PublicIds.next());
        ns.setNamespaceType("user");
        ns.setUserId(user.getId());
        ns.setSlug(normalized);
        ns.setDisplayName(user.getNickname());
        namespaces.save(ns);

        auditService.append(normalized, "user.register", "user:" + user.getPublicId(), "success",
                ip, userAgent, null);
        return authResult(user, ns.getPublicId(), sessionService.issue(user, null, ip));
    }

    @Transactional
    public AuthResult login(String username, String password, String ip, String userAgent) {
        String normalized = normalizeUsername(username);
        enforceRateLimit("login:ip:" + ip);
        enforceRateLimit("login:user:" + normalized);

        UserEntity user = users.findByUsername(normalized).orElse(null);
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            // 失败计数/锁定/失败审计走独立事务提交：随后抛出的异常会回滚本事务，但不能丢失这些事实
            loginGuard.recordLoginFailure(normalized, user, ip, userAgent);
            counters.loginFailure();
            throw new ApiException(ErrorCode.UNAUTHENTICATED, GENERIC_LOGIN_FAILURE);
        }
        if (!"active".equals(user.getStatus())) {
            auditService.append(normalized, "auth.login_fail", "user:" + user.getPublicId(),
                    "status_" + user.getStatus(), ip, userAgent, null);
            counters.loginFailure();
            throw new ApiException(ErrorCode.UNAUTHENTICATED, GENERIC_LOGIN_FAILURE);
        }
        users.resetFailedAttempts(user.getId());
        NamespaceEntity ns = namespaces.findByUserIdAndNamespaceType(user.getId(), "user").orElse(null);
        auditService.append(normalized, "auth.login", "user:" + user.getPublicId(), "success",
                ip, userAgent, null);
        return authResult(user, ns == null ? null : ns.getPublicId(), sessionService.issue(user, null, ip));
    }

    /** refresh：Origin + CSRF + Cookie 三匹配（02 §6.1 末段）。 */
    @Transactional
    public AuthResult refresh(String refreshToken, String csrfToken, String origin, String clientMeta) {
        requireAllowedOrigin(origin);
        if (csrfToken == null || csrfToken.isBlank()) {
            throw new ApiException(ErrorCode.CSRF_INVALID, "缺少 X-CSRF-Token");
        }
        IssuedSession issued = sessionService.rotate(refreshToken, csrfToken, clientMeta);
        UserEntity user = users.findById(issued.userId()).orElseThrow();
        NamespaceEntity ns = namespaces.findByUserIdAndNamespaceType(user.getId(), "user").orElse(null);
        return authResult(user, ns == null ? null : ns.getPublicId(), issued);
    }

    @Transactional
    public void logout(String refreshToken, String csrfToken, String origin) {
        requireAllowedOrigin(origin);
        if (csrfToken == null || csrfToken.isBlank()) {
            throw new ApiException(ErrorCode.CSRF_INVALID, "缺少 X-CSRF-Token");
        }
        sessionService.revokeByToken(refreshToken);
    }

    @Transactional
    public void logoutAll(CurrentPrincipal principal) {
        UserEntity user = users.findById(principal.userId()).orElseThrow();
        user.setAuthVersion(user.getAuthVersion() + 1);
        user.setUpdatedAt(OffsetDateTime.now());
        users.save(user);
        sessionService.revokeAllForUser(user.getId());
        auditService.appendSimple(user.getUsername(), "auth.logout_all", "user:" + user.getPublicId(), "success");
    }

    @Transactional
    public void changePassword(CurrentPrincipal principal, String currentPassword, String newPassword) {
        UserEntity user = users.findById(principal.userId()).orElseThrow();
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            auditService.appendSimple(user.getUsername(), "auth.change_password_fail",
                    "user:" + user.getPublicId(), "denied");
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "原密码错误");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setAuthVersion(user.getAuthVersion() + 1);
        user.setUpdatedAt(OffsetDateTime.now());
        users.save(user);
        sessionService.revokeAllForUser(user.getId());
        auditService.appendSimple(user.getUsername(), "auth.change_password",
                "user:" + user.getPublicId(), "success");
    }

    @Transactional(readOnly = true)
    public UserView getMe(CurrentPrincipal principal) {
        UserEntity user = users.findById(principal.userId()).orElseThrow();
        NamespaceEntity ns = namespaces.findByUserIdAndNamespaceType(user.getId(), "user").orElse(null);
        List<String> roles = userRoles.findActiveRoleCodes(user.getId());
        return new UserView(user.getPublicId().toString(), user.getUsername(), user.getNickname(),
                user.getStatus(), ns == null ? null : ns.getPublicId().toString(), roles,
                user.getProfileVersion(), user.getCreatedAt());
    }

    @Transactional
    public UserView updateProfile(CurrentPrincipal principal, String nickname, String ifMatch) {
        UserEntity user = users.findById(principal.userId()).orElseThrow();
        ETags.requireMatch(ifMatch, ETags.ofVersion(user.getProfileVersion()), "用户资料");
        user.setNickname(nickname);
        user.setProfileVersion(user.getProfileVersion() + 1);
        user.setUpdatedAt(OffsetDateTime.now());
        users.save(user);
        return getMe(principal);
    }

    /** Access JWT 在线校验：用户存在、状态 active、auth_version 一致（02 §9 高风险立即失效）。 */
    @Transactional(readOnly = true)
    public CurrentPrincipal resolvePrincipal(JWTClaimsSet claims) {
        Long userId;
        try {
            userId = Long.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Access Token 主体无效");
        }
        UserEntity user = users.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "用户不存在"));
        if (!user.isActive()) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "用户已被锁定或禁用");
        }
        Object av = claims.getClaim("auth_version");
        if (av == null || ((Number) av).longValue() != user.getAuthVersion()) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "会话已失效，请重新登录");
        }
        List<String> roles = userRoles.findActiveRoleCodes(userId);
        return new CurrentPrincipal(userId, user.getPublicId(), user.getUsername(),
                user.getAuthVersion(), String.valueOf(claims.getClaim("sid")), Set.copyOf(roles),
                Set.copyOf(userRoles.findActivePermissionCodes(userId)));
    }

    /** platform_admin 解锁被锁定账户（04 §4.1）。 */
    @Transactional
    public void unlockUser(CurrentPrincipal admin, UUID userPublicId) {
        if (!admin.isPlatformAdmin()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "仅 platform_admin 可解锁账户");
        }
        UserEntity user = users.findByPublicId(userPublicId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));
        if ("locked".equals(user.getStatus())) {
            user.setStatus("active");
            users.resetFailedAttempts(user.getId());
            user.setUpdatedAt(OffsetDateTime.now());
            users.save(user);
        }
        auditService.appendSimple(admin.username(), "admin.user_unlock",
                "user:" + userPublicId, "success");
    }

    private void enforceRateLimit(String dimension) {
        RateLimiter.Verdict v = rateLimiter.tryAcquire(dimension);
        if (!v.allowed()) {
            throw new ApiException(ErrorCode.RATE_LIMITED, "请求过于频繁，请稍后再试", List.of(),
                    java.util.Map.of("Retry-After", String.valueOf(v.retryAfterSeconds())));
        }
    }

    private void requireAllowedOrigin(String origin) {
        if (origin == null || origin.isBlank() || !props.allowedOrigins().contains(origin)) {
            throw new ApiException(ErrorCode.CSRF_INVALID, "Origin 校验失败");
        }
    }

    private AuthResult authResult(UserEntity user, UUID namespacePublicId, IssuedSession session) {
        List<String> roles = userRoles.findActiveRoleCodes(user.getId());
        UserView view = new UserView(user.getPublicId().toString(), user.getUsername(), user.getNickname(),
                user.getStatus(), namespacePublicId == null ? null : namespacePublicId.toString(), roles,
                user.getProfileVersion(), user.getCreatedAt());
        return new AuthResult(view, session);
    }

    static String normalizeUsername(String username) {
        if (username == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "用户名不能为空",
                    List.of(new ApiException.Detail("username", "required")));
        }
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME_PATTERN.matcher(normalized).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "用户名必须为 3-31 位小写字母/数字，可含 _ 和 -，且以字母或数字开头",
                    List.of(new ApiException.Detail("username", "invalid_format")));
        }
        return normalized;
    }
}
