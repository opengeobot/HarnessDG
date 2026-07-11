/*
 * 功能: 认证应用服务，编排登录、刷新轮换、凭据交换、登出、当前主体与改密用例。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.application;

import com.aihub.identity.domain.AgentIdentity;
import com.aihub.identity.domain.AgentIdentityRepository;
import com.aihub.identity.domain.LocalUser;
import com.aihub.identity.domain.LocalUserRepository;
import com.aihub.audit.domain.AuditResult;
import com.aihub.authorization.application.EffectiveScopeResolver;
import com.aihub.identity.domain.RefreshTokenRecord;
import com.aihub.identity.domain.RefreshTokenRepository;
import com.aihub.shared.error.AuthenticationException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.shared.identity.PrincipalType;
import com.aihub.shared.security.IssuedToken;
import com.aihub.shared.security.JwtClaims;
import com.aihub.shared.security.PasswordHasher;
import com.aihub.shared.security.PasswordPolicy;
import com.aihub.shared.security.TokenIssueRequest;
import com.aihub.shared.security.TokenSigner;
import com.aihub.shared.security.TokenType;
import com.aihub.shared.security.TokenVerifier;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证应用服务。
 *
 * <p>编排本地用户登录、刷新令牌轮换与重放检测、Agent/Service 凭据交换、登出、当前主体查询与改密。
 * 所有 JWT/口令/凭据明文仅在方法内短暂使用，绝不写入日志或审计正文。
 */
@Service
public class AuthenticationApplicationService {

    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationApplicationService.class);

    private final LocalUserRepository userRepository;
    private final AgentIdentityRepository agentRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenSigner tokenSigner;
    private final TokenVerifier tokenVerifier;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicy passwordPolicy;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final AuditPort auditPort;
    private final EffectiveScopeResolver effectiveScopeResolver;

    public AuthenticationApplicationService(LocalUserRepository userRepository,
                                            AgentIdentityRepository agentRepository,
                                            RefreshTokenRepository refreshTokenRepository,
                                            TokenSigner tokenSigner,
                                            TokenVerifier tokenVerifier,
                                            PasswordHasher passwordHasher,
                                            PasswordPolicy passwordPolicy,
                                            IdGenerator idGenerator,
                                            Clock clock,
                                            AuditPort auditPort,
                                            EffectiveScopeResolver effectiveScopeResolver) {
        this.userRepository = userRepository;
        this.agentRepository = agentRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenSigner = tokenSigner;
        this.tokenVerifier = tokenVerifier;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.auditPort = auditPort;
        this.effectiveScopeResolver = effectiveScopeResolver;
    }

    /**
     * 本地用户登录：校验状态与口令，签发 access+refresh，记录刷新令牌族。
     */
    @Transactional
    public TokenPairResult login(String username, String rawPassword) {
        LocalUser user = userRepository.findByUsername(username)
                // 用户名不存在与口令错误统一返回 INVALID_CREDENTIALS，避免账号枚举。
                .orElseThrow(() -> {
                    audit("AUTH_LOGIN_FAILED", AuditResult.FAILED, null, null,
                            ErrorCode.AUTH_INVALID_CREDENTIALS.name(), Map.of("username", username));
                    return invalidCredentials();
                });

        if (user.isDisabled()) {
            audit("AUTH_LOGIN_FAILED", AuditResult.DENIED, user.principalId(), user.principalId(),
                    ErrorCode.AUTH_ACCOUNT_DISABLED.name(), Map.of("username", username));
            throw new AuthenticationException(ErrorCode.AUTH_ACCOUNT_DISABLED, "account disabled", Map.of());
        }
        if (user.isLocked(clock.instant())) {
            audit("AUTH_LOGIN_FAILED", AuditResult.DENIED, user.principalId(), user.principalId(),
                    ErrorCode.AUTH_ACCOUNT_LOCKED.name(), Map.of("username", username));
            throw new AuthenticationException(ErrorCode.AUTH_ACCOUNT_LOCKED, "account locked", Map.of());
        }
        if (!user.matchesPassword(rawPassword, passwordHasher)) {
            user.recordLoginFailure(clock.instant());
            userRepository.update(user);
            if (user.isLocked(clock.instant())) {
                audit("AUTH_LOGIN_FAILED", AuditResult.DENIED, user.principalId(), user.principalId(),
                        ErrorCode.AUTH_ACCOUNT_LOCKED.name(), Map.of("username", username));
                throw new AuthenticationException(ErrorCode.AUTH_ACCOUNT_LOCKED, "account locked", Map.of());
            }
            audit("AUTH_LOGIN_FAILED", AuditResult.FAILED, user.principalId(), user.principalId(),
                    ErrorCode.AUTH_INVALID_CREDENTIALS.name(), Map.of("username", username));
            throw invalidCredentials();
        }

        user.recordLoginSuccess(clock.instant());
        userRepository.update(user);

        String family = idGenerator.generate(IdPrefix.TOKEN);
        TokenPairResult result = issueUserTokenPair(user, family);
        audit("AUTH_LOGIN_SUCCEEDED", AuditResult.SUCCEEDED, user.principalId(), user.principalId(),
                null, Map.of("username", username));
        return result;
    }

    /**
     * 刷新令牌轮换：校验 refresh JWT，检测重放（整族吊销），轮换旧 jti 并签发新令牌对。
     */
    @Transactional
    public TokenPairResult refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AuthenticationException(ErrorCode.AUTH_UNAUTHENTICATED, "missing refresh token", Map.of());
        }
        JwtClaims claims = tokenVerifier.verify(refreshToken);
        if (claims.tokenType() != TokenType.REFRESH) {
            throw new AuthenticationException(ErrorCode.AUTH_UNAUTHENTICATED, "not a refresh token", Map.of());
        }

        RefreshTokenRecord record = refreshTokenRepository.findByJwtId(claims.jwtId())
                .orElseThrow(() -> new AuthenticationException(
                        ErrorCode.AUTH_UNAUTHENTICATED, "unknown refresh token", Map.of()));

        if (!record.isActive()) {
            // 已轮换/已吊销的 refresh 再次出现 → 重放：吊销整族，拒绝。
            refreshTokenRepository.revokeFamily(record.tokenFamily());
            LOG.warn("refresh token replay detected, revoking family. principalId={}", record.principalId());
            audit("AUTH_TOKEN_REPLAY_REJECTED", AuditResult.DENIED, record.principalId(), record.tokenFamily(),
                    ErrorCode.AUTH_REFRESH_REPLAYED.name(), Map.of("tokenFamily", record.tokenFamily()));
            throw new AuthenticationException(ErrorCode.AUTH_REFRESH_REPLAYED, "refresh token replayed", Map.of());
        }

        LocalUser user = userRepository.findByPrincipalId(record.principalId())
                .orElseThrow(() -> new AuthenticationException(
                        ErrorCode.AUTH_UNAUTHENTICATED, "principal not found", Map.of()));
        if (user.isDisabled()) {
            throw new AuthenticationException(ErrorCode.AUTH_ACCOUNT_DISABLED, "account disabled", Map.of());
        }
        // 改密后 tokenVersion 递增，旧 refresh 立即失效。
        if (claims.tokenVersion() < user.tokenVersion()) {
            throw new AuthenticationException(ErrorCode.AUTH_UNAUTHENTICATED, "refresh token superseded", Map.of());
        }

        TokenPairResult result = issueUserTokenPair(user, record.tokenFamily());
        // 轮换：旧 jti 标记 ROTATED，记录后继 jti（新 refresh 的 jti）。
        refreshTokenRepository.markRotated(record.jti(), jtiOf(result.refreshToken()));
        audit("AUTH_TOKEN_REFRESHED", AuditResult.SUCCEEDED, user.principalId(), record.tokenFamily(),
                null, Map.of("tokenFamily", record.tokenFamily()));
        return result;
    }

    /**
     * 凭据交换：Agent/Service/API Client 用稳定 ID + 凭据换取受限 access JWT。
     */
    @Transactional(readOnly = true)
    public TokenPairResult exchangeClientCredential(String subjectId, String rawCredential) {
        AgentIdentity agent = agentRepository.findByAgentId(subjectId)
                .orElseThrow(() -> {
                    audit("CLIENT_TOKEN_REJECTED", AuditResult.FAILED, null, subjectId,
                            ErrorCode.AUTH_INVALID_CREDENTIALS.name(), Map.of("subjectId", subjectId));
                    return new AuthenticationException(
                            ErrorCode.AUTH_INVALID_CREDENTIALS, "invalid credentials", Map.of());
                });
        if (agent.isDisabled()) {
            audit("CLIENT_TOKEN_REJECTED", AuditResult.DENIED, agent.principalId(), agent.principalId(),
                    ErrorCode.AUTH_ACCOUNT_DISABLED.name(), Map.of("subjectId", subjectId));
            throw new AuthenticationException(ErrorCode.AUTH_ACCOUNT_DISABLED, "agent disabled", Map.of());
        }
        if (!agent.matchesCredential(rawCredential, passwordHasher)) {
            audit("CLIENT_TOKEN_REJECTED", AuditResult.FAILED, agent.principalId(), agent.principalId(),
                    ErrorCode.AUTH_INVALID_CREDENTIALS.name(), Map.of("subjectId", subjectId));
            throw new AuthenticationException(ErrorCode.AUTH_INVALID_CREDENTIALS, "invalid credentials", Map.of());
        }

        IssuedToken access = tokenSigner.issue(new TokenIssueRequest(
                agent.principalId(), PrincipalType.AGENT, agent.tokenVersion(), agent.scopes(), TokenType.ACCESS));
        long expiresIn = secondsUntil(access);
        CurrentPrincipalView principal = new CurrentPrincipalView(
                agent.principalId(), null, PrincipalType.AGENT, agent.principalId(),
                agent.displayName(), null, List.of(), List.copyOf(agent.scopes()), "zh-CN", false);
        audit("CLIENT_TOKEN_ISSUED", AuditResult.SUCCEEDED, agent.principalId(), agent.principalId(),
                null, Map.of("subjectId", subjectId));
        // Agent 凭据交换默认不签发刷新令牌（凭据可再次交换）。
        return new TokenPairResult(access.token(), null, expiresIn, 0L, principal);
    }

    /**
     * 登出：吊销当前刷新令牌所属的整个 Token Family。
     */
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        try {
            JwtClaims claims = tokenVerifier.verify(refreshToken);
            refreshTokenRepository.findByJwtId(claims.jwtId())
                    .ifPresent(record -> {
                        refreshTokenRepository.revokeFamily(record.tokenFamily());
                        audit("AUTH_LOGOUT", AuditResult.SUCCEEDED, record.principalId(), record.tokenFamily(),
                                null, Map.of("tokenFamily", record.tokenFamily()));
                    });
        } catch (AuthenticationException ex) {
            // 登出对无效/过期 token 幂等：不抛错。
            LOG.debug("logout with invalid refresh token, ignored");
        }
    }

    /**
     * 当前主体概要（用户或 Agent）。
     */
    @Transactional(readOnly = true)
    public CurrentPrincipalView currentPrincipal(String principalId, PrincipalType principalType) {
        if (principalType == PrincipalType.AGENT) {
            AgentIdentity agent = agentRepository.findByPrincipalId(principalId)
                    .orElseThrow(() -> new NotFoundException(
                            ErrorCode.AGENT_NOT_FOUND, "agent not found", Map.of()));
            return new CurrentPrincipalView(agent.principalId(), null, PrincipalType.AGENT, agent.principalId(),
                    agent.displayName(), null, List.of(), List.copyOf(agent.scopes()), "zh-CN", false);
        }
        LocalUser user = userRepository.findByPrincipalId(principalId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND, "user not found", Map.of()));
        return toPrincipalView(user);
    }

    /**
     * 修改当前用户口令：校验旧口令、新口令策略，更新并使其余 Token 失效。
     */
    @Transactional
    public void changePassword(String principalId, String currentPassword, String newPassword) {
        LocalUser user = userRepository.findByPrincipalId(principalId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND, "user not found", Map.of()));
        if (!user.matchesPassword(currentPassword, passwordHasher)) {
            throw new AuthenticationException(ErrorCode.AUTH_INVALID_CREDENTIALS, "invalid credentials", Map.of());
        }
        List<String> violations = passwordPolicy.validate(newPassword);
        if (!violations.isEmpty()) {
            throw new ValidationException(ErrorCode.PASSWORD_POLICY_VIOLATION,
                    "password policy violation", Map.of("violations", violations));
        }
        user.changePassword(passwordHasher.hash(newPassword), clock.instant());
        userRepository.update(user);
        // 改密后吊销其全部刷新令牌（access 由 tokenVersion 递增自动失效）。
        refreshTokenRepository.revokeAllByPrincipal(principalId);
        audit("USER_PASSWORD_CHANGED", AuditResult.SUCCEEDED, principalId, principalId, null, Map.of());
    }

    /**
     * 记录一条 identity 审计事件（旁路，不影响主流程）。attributes 不得含凭据/令牌明文。
     */
    private void audit(String eventType, AuditResult result, String actorId, String targetId,
                       String errorCode, Map<String, Object> attributes) {
        auditPort.record(eventType, result, actorId, targetId, errorCode, attributes);
    }

    private TokenPairResult issueUserTokenPair(LocalUser user, String family) {
        Set<String> scopes = effectiveScopeResolver.resolve(user.principalId(), user.scopes());
        IssuedToken access = tokenSigner.issue(new TokenIssueRequest(
                user.principalId(), PrincipalType.USER, user.tokenVersion(), scopes, TokenType.ACCESS));
        IssuedToken refresh = tokenSigner.issue(new TokenIssueRequest(
                user.principalId(), PrincipalType.USER, user.tokenVersion(), scopes, TokenType.REFRESH));

        RefreshTokenRecord record = new RefreshTokenRecord.Builder()
                .tokenId(idGenerator.generate(IdPrefix.TOKEN))
                .jti(refresh.claims().jwtId())
                .tokenFamily(family)
                .principalId(user.principalId())
                .principalType(PrincipalType.USER)
                .scopes(scopes)
                .issuedAt(refresh.claims().issuedAt())
                .expiresAt(refresh.claims().expiresAt())
                .build();
        refreshTokenRepository.save(record);

        return new TokenPairResult(access.token(), refresh.token(),
                secondsUntil(access), secondsUntil(refresh), toPrincipalView(user));
    }

    private CurrentPrincipalView toPrincipalView(LocalUser user) {
        Set<String> effectiveScopes = effectiveScopeResolver.resolve(user.principalId(), user.scopes());
        return new CurrentPrincipalView(
                user.principalId(), user.userId(), PrincipalType.USER, user.principalId(),
                user.displayName(), null, List.of(), List.copyOf(effectiveScopes),
                user.locale(), user.mustChangePassword());
    }

    private long secondsUntil(IssuedToken token) {
        return java.time.Duration.between(token.claims().issuedAt(), token.claims().expiresAt()).getSeconds();
    }

    private String jtiOf(String token) {
        return tokenVerifier.verify(token).jwtId();
    }

    private AuthenticationException invalidCredentials() {
        return new AuthenticationException(ErrorCode.AUTH_INVALID_CREDENTIALS, "invalid credentials", Map.of());
    }
}
