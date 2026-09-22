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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 认证应用服务。
 *
 * <p>编排本地用户登录、刷新令牌轮换与重放检测、Agent/Service 凭据交换、登出、当前主体查询与改密。
 * 所有 JWT/口令/凭据明文仅在方法内短暂使用，绝不写入日志或审计正文。
 *
 * <p>登录失败路径使用独立事务（{@link #failureTransaction}）：认证失败最终以异常抛出，
 * 若与成功路径共用事务，失败计数、锁定状态与失败审计会随异常回滚被一并撤销，
 * 使账户锁定与失败沉淀整体失效。
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

    /** 登录失败副作用（计数/锁定/失败审计）专用事务模板，独立于调用方事务提交。 */
    private final TransactionTemplate failureTransaction;

    /**
     * 令牌轮换专用事务模板：在**单一事务**内完成「jti 的原子 CAS + 同 Family 新摘要落库」
     * （REQ-IAM-004 要求二者同事务）。
     *
     * <p>轮换抢占失败者要另行开启事务做整族吊销，故 CAS 不得处于外层长事务中：
     * 抢占失败的 CAS 会阻塞在胜者的行锁上，使外层事务保留该行锁；
     * 此时若在同一线程内嵌套 {@code REQUIRES_NEW} 事务去更新同一行，
     * 内层将永远等待外层持有的锁（自锁），请求既不返回也不释放连接。
     */
    private final TransactionTemplate rotationTransaction;

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
                                            EffectiveScopeResolver effectiveScopeResolver,
                                            PlatformTransactionManager transactionManager) {
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
        this.failureTransaction = new TransactionTemplate(transactionManager);
        this.failureTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.rotationTransaction = new TransactionTemplate(transactionManager);
    }

    /**
     * 本地用户登录：校验状态与口令，签发 access+refresh，记录刷新令牌族。
     *
     * <p>失败与拒绝路径的副作用经 {@link #failureTransaction} 独立提交后再抛出异常，
     * 确保失败计数、锁定状态与 {@code AUTH_LOGIN_FAILED} 审计不被回滚。
     */
    @Transactional
    public TokenPairResult login(String username, String rawPassword) {
        LocalUser user = userRepository.findByUsername(username)
                // 用户名不存在与口令错误统一返回 INVALID_CREDENTIALS，避免账号枚举。
                .orElseThrow(() -> {
                    recordDeniedInOwnTransaction(null, username, ErrorCode.AUTH_INVALID_CREDENTIALS,
                            AuditResult.FAILED);
                    return invalidCredentials();
                });

        if (user.isDisabled()) {
            recordDeniedInOwnTransaction(user.principalId(), username, ErrorCode.AUTH_ACCOUNT_DISABLED,
                    AuditResult.DENIED);
            throw new AuthenticationException(ErrorCode.AUTH_ACCOUNT_DISABLED, "account disabled", Map.of());
        }
        if (user.isLocked(clock.instant())) {
            recordDeniedInOwnTransaction(user.principalId(), username, ErrorCode.AUTH_ACCOUNT_LOCKED,
                    AuditResult.DENIED);
            throw new AuthenticationException(ErrorCode.AUTH_ACCOUNT_LOCKED, "account locked", Map.of());
        }
        if (!user.matchesPassword(rawPassword, passwordHasher)) {
            LocalUser updated = recordFailureInOwnTransaction(user, username);
            if (updated.isLocked(clock.instant())) {
                throw new AuthenticationException(ErrorCode.AUTH_ACCOUNT_LOCKED, "account locked", Map.of());
            }
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
     * 在独立事务中原子递增失败计数、按阈值锁定，并写入 {@code AUTH_LOGIN_FAILED} 审计。
     *
     * <p>计数与审计在同一事务内提交，保证「状态和审计一致」；事务在抛出认证异常前完成提交。
     *
     * @return 更新后的用户（含最新失败计数与锁定状态）
     */
    private LocalUser recordFailureInOwnTransaction(LocalUser user, String username) {
        LocalUser updated = failureTransaction.execute(status ->
                userRepository.recordLoginFailureAtomically(user.userId(), clock.instant()).orElse(user));
        if (updated == null) {
            return user;
        }
        boolean locked = updated.isLocked(clock.instant());
        failureTransaction.executeWithoutResult(status -> audit("AUTH_LOGIN_FAILED",
                locked ? AuditResult.DENIED : AuditResult.FAILED,
                updated.principalId(), updated.principalId(),
                (locked ? ErrorCode.AUTH_ACCOUNT_LOCKED : ErrorCode.AUTH_INVALID_CREDENTIALS).name(),
                Map.of("username", username)));
        return updated;
    }

    /**
     * 在独立事务中写入拒绝审计（无状态变更的失败/拒绝路径：未知用户、禁用、锁定中）。
     */
    private void recordDeniedInOwnTransaction(String principalId, String username, ErrorCode errorCode,
                                              AuditResult result) {
        failureTransaction.executeWithoutResult(status -> audit("AUTH_LOGIN_FAILED", result,
                principalId, principalId, errorCode.name(), Map.of("username", username)));
    }

    /**
     * 刷新令牌轮换：校验 refresh JWT，检测重放（整族吊销），轮换旧 jti 并签发新令牌对。
     *
     * <p>轮换在 {@link #rotationTransaction} 的单一事务内完成「jti 原子 CAS + 同 Family 新摘要落库
     * + 成功审计」（REQ-IAM-004）；重放检测的整族吊销与拒绝审计经 {@link #failureTransaction} 独立提交，
     * 既不随随后抛出的认证异常回滚，也不会与轮换事务互相持锁（详见 {@link #rotationTransaction}）。
     *
     * <p>抢占失败一律按重放处理：无论原因是攻击者重放还是并发竞争，此时该 jti 均已不是 ACTIVE，
     * 与状态机「ROTATED jti 再次使用触发 Family 全部 REVOKED」一致，不引入可绕过重放检测的时间窗。
     */
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

        // 以原子 CAS 抢占该 jti：只有把 ACTIVE 成功翻转为 ROTATED 的请求才是唯一胜者。
        // 抢占失败意味着该 jti 已被消费/吊销 → 按重放处理（整族吊销）。
        // 抢占成功后须与新摘要落库同事务（REQ-IAM-004），故轮换全过程在 rotationTransaction 内执行。
        TokenPairResult result = rotationTransaction.execute(status -> {
            if (!refreshTokenRepository.claimForRotation(record.jti())) {
                return null;
            }
            LocalUser owner = userRepository.findByPrincipalId(record.principalId())
                    .orElseThrow(() -> new AuthenticationException(
                            ErrorCode.AUTH_UNAUTHENTICATED, "principal not found", Map.of()));
            if (owner.isDisabled()) {
                throw new AuthenticationException(ErrorCode.AUTH_ACCOUNT_DISABLED, "account disabled", Map.of());
            }
            // 改密后 tokenVersion 递增，旧 refresh 立即失效。
            if (claims.tokenVersion() < owner.tokenVersion()) {
                throw new AuthenticationException(ErrorCode.AUTH_UNAUTHENTICATED,
                        "refresh token superseded", Map.of());
            }
            TokenPairResult pair = issueUserTokenPair(owner, record.tokenFamily());
            // 记录后继 jti，便于审计与排障（状态已由 claimForRotation 原子置为 ROTATED）。
            refreshTokenRepository.markRotated(record.jti(), jtiOf(pair.refreshToken()));
            audit("AUTH_TOKEN_REFRESHED", AuditResult.SUCCEEDED, owner.principalId(), record.tokenFamily(),
                    null, Map.of("tokenFamily", record.tokenFamily()));
            return pair;
        });
        if (result == null) {
            revokeFamilyInOwnTransaction(record);
            throw new AuthenticationException(ErrorCode.AUTH_REFRESH_REPLAYED, "refresh token replayed", Map.of());
        }
        return result;
    }

    /**
     * 在独立事务中吊销整个 Token Family 并写入 {@code AUTH_TOKEN_REPLAY_REJECTED} 审计。
     *
     * <p>重放检测最终以异常对外表达，若与调用方共用事务，吊销与拒绝审计会随异常回滚，
     * 使「被盗令牌被切断」这一安全保证失效。
     */
    private void revokeFamilyInOwnTransaction(RefreshTokenRecord record) {
        failureTransaction.executeWithoutResult(status -> {
            int revoked = refreshTokenRepository.revokeFamily(record.tokenFamily());
            LOG.warn("refresh token replay detected, revoked family. principalId={} revokedRows={}",
                    record.principalId(), revoked);
            audit("AUTH_TOKEN_REPLAY_REJECTED", AuditResult.DENIED, record.principalId(), record.tokenFamily(),
                    ErrorCode.AUTH_REFRESH_REPLAYED.name(), Map.of("tokenFamily", record.tokenFamily()));
        });
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
