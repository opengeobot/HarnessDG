/*
 * 功能: 用户管理应用服务，编排用户创建、查询、更新、启停与重置口令用例。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.application;

import com.aihub.audit.domain.AuditResult;
import com.aihub.identity.application.IdentityCommands.CreateUserCommand;
import com.aihub.identity.application.IdentityCommands.UpdateUserCommand;
import com.aihub.identity.domain.LocalUser;
import com.aihub.identity.domain.LocalUserRepository;
import com.aihub.identity.domain.PrincipalAccount;
import com.aihub.identity.domain.RefreshTokenRepository;
import com.aihub.identity.domain.UserStatus;
import com.aihub.shared.api.PageResult;
import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.shared.identity.PrincipalType;
import com.aihub.shared.security.PasswordHasher;
import com.aihub.shared.security.PasswordPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 用户管理应用服务。
 *
 * <p>管理端用例编排：创建用户（同时建立主体）、列表/详情、更新资料、启用/禁用（即时失效）、
 * 重置口令（强制下次修改）。临时口令明文绝不写日志或审计正文。
 *
 * <p>每个管理动作写入对应审计事件（{@code USER_CREATED/UPDATED/ENABLED/DISABLED/PASSWORD_RESET}，
 * 与 OpenAPI {@code x-audit-event} 一致）；禁用动作额外校验「不得禁用最后一个有效平台管理员」。
 */
@Service
public class UserManagementApplicationService {

    /** 判定「平台管理员」所用的权限码；与 REQ-IAM-006 的 user:manage 一致。 */
    private static final String USER_MANAGE_SCOPE = "user:manage";

    private final LocalUserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicy passwordPolicy;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final AuditPort auditPort;

    /**
     * 拒绝审计专用事务模板（独立提交）。
     *
     * <p>拒绝路径最终以异常对外表达；若与调用方共用事务，拒绝审计会随异常回滚被撤销，
     * 使「拒绝必须留痕」失效。
     */
    private final TransactionTemplate denialTransaction;

    public UserManagementApplicationService(LocalUserRepository userRepository,
                                            RefreshTokenRepository refreshTokenRepository,
                                            PasswordHasher passwordHasher,
                                            PasswordPolicy passwordPolicy,
                                            IdGenerator idGenerator,
                                            Clock clock,
                                            AuditPort auditPort,
                                            PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.auditPort = auditPort;
        this.denialTransaction = new TransactionTemplate(transactionManager);
        this.denialTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 创建本地用户：用户名唯一校验、临时口令策略校验，同时创建主体，强制下次改密。
     */
    @Transactional
    public UserView createUser(CreateUserCommand command) {
        if (command.username() == null || command.username().isBlank()) {
            throw new ValidationException("username is required");
        }
        if (userRepository.existsByUsername(command.username())) {
            throw new ConflictException(ErrorCode.USER_ALREADY_EXISTS,
                    "username already exists", Map.of("username", command.username()));
        }
        List<String> violations = passwordPolicy.validate(command.temporaryPassword());
        if (!violations.isEmpty()) {
            throw new ValidationException(ErrorCode.PASSWORD_POLICY_VIOLATION,
                    "password policy violation", Map.of("violations", violations));
        }

        Instant now = clock.instant();
        String principalId = idGenerator.generate(IdPrefix.PRINCIPAL);
        String userId = idGenerator.generate(IdPrefix.USER);
        PrincipalAccount principal = new PrincipalAccount(
                principalId, PrincipalType.USER, command.displayName(), "ACTIVE", now, now);
        LocalUser user = new LocalUser.Builder()
                .userId(userId)
                .principalId(principalId)
                .username(command.username())
                .displayName(command.displayName())
                .email(command.email())
                .locale(command.locale())
                .passwordHash(passwordHasher.hash(command.temporaryPassword()))
                .scopes(command.scopes() == null ? java.util.Set.of() : command.scopes())
                .tokenVersion(0L)
                .mustChangePassword(true)
                .status(UserStatus.PENDING_ACTIVATION)
                .createdAt(now)
                .updatedAt(now)
                .rowVersion(0L)
                .build();
        userRepository.create(principal, user);
        audit("USER_CREATED", AuditResult.SUCCEEDED, userId, user.principalId(),
                Map.of("username", user.username()));
        return UserView.from(user);
    }

    /**
     * 分页查询用户列表。
     */
    @Transactional(readOnly = true)
    public PageResult<UserView> listUsers(String keyword, UserStatus status, int page, int size) {
        PageResult<LocalUser> result = userRepository.search(keyword, status, page, size);
        return new PageResult<>(result.items().stream().map(UserView::from).toList(),
                result.total(), result.page(), result.pageSize());
    }

    /**
     * 查询用户详情。
     */
    @Transactional(readOnly = true)
    public UserView getUser(String userId) {
        return UserView.from(requireUser(userId));
    }

    /**
     * 更新用户资料。
     */
    @Transactional
    public UserView updateUser(String userId, UpdateUserCommand command) {
        LocalUser user = requireUser(userId);
        user.updateProfile(command.displayName(), command.email(), command.locale(), clock.instant());
        userRepository.update(user);
        audit("USER_UPDATED", AuditResult.SUCCEEDED, user.userId(), user.principalId(),
                Map.of("username", user.username()));
        return UserView.from(user);
    }

    /**
     * 启用用户。
     */
    @Transactional
    public void enableUser(String userId) {
        LocalUser user = requireUser(userId);
        user.enable(clock.instant());
        userRepository.update(user);
        audit("USER_ENABLED", AuditResult.SUCCEEDED, user.userId(), user.principalId(),
                Map.of("username", user.username()));
    }

    /**
     * 禁用用户并吊销其全部刷新令牌（access 由 tokenVersion 递增自动失效）。
     *
     * <p>拒绝禁用最后一个有效平台管理员（REQ-IAM-006）：拒绝时返回
     * {@link ErrorCode#COMMON_INVALID_ARGUMENT} 并写入拒绝审计。
     */
    @Transactional
    public void disableUser(String userId) {
        LocalUser user = requireUser(userId);
        if (isLastActivePlatformAdmin(user)) {
            // 拒绝审计必须独立提交：随后抛出的校验异常会回滚调用方事务。
            denialTransaction.executeWithoutResult(status -> auditPort.record(
                    "USER_DISABLED", AuditResult.DENIED, user.userId(), user.principalId(),
                    ErrorCode.COMMON_INVALID_ARGUMENT.name(),
                    Map.of("username", user.username(), "reason", "lastActivePlatformAdmin")));
            throw new ValidationException(ErrorCode.COMMON_INVALID_ARGUMENT,
                    "cannot disable the last active platform administrator",
                    Map.of("userId", userId));
        }
        user.disable(clock.instant());
        userRepository.update(user);
        refreshTokenRepository.revokeAllByPrincipal(user.principalId());
        audit("USER_DISABLED", AuditResult.SUCCEEDED, user.userId(), user.principalId(),
                Map.of("username", user.username()));
    }

    /**
     * 重置口令：设置临时口令、强制下次修改、吊销其全部刷新令牌。临时口令明文不返回也不入日志。
     */
    @Transactional
    public void resetPassword(String userId, String temporaryPassword) {
        List<String> violations = passwordPolicy.validate(temporaryPassword);
        if (!violations.isEmpty()) {
            throw new ValidationException(ErrorCode.PASSWORD_POLICY_VIOLATION,
                    "password policy violation", Map.of("violations", violations));
        }
        LocalUser user = requireUser(userId);
        user.resetPassword(passwordHasher.hash(temporaryPassword), clock.instant());
        userRepository.update(user);
        refreshTokenRepository.revokeAllByPrincipal(user.principalId());
        // 审计只记录目标身份，绝不记录临时口令。
        audit("USER_PASSWORD_RESET", AuditResult.SUCCEEDED, user.userId(), user.principalId(),
                Map.of("username", user.username()));
    }

    /**
     * 判断目标用户是否为「当前唯一的活跃且持有 {@code user:manage} 的平台管理员」。
     *
     * <p>仅当该用户自身处于 ACTIVE 且持有该权限、且库中活跃管理员总数为 1 时成立。
     */
    private boolean isLastActivePlatformAdmin(LocalUser user) {
        boolean isAdmin = user.scopes().contains(USER_MANAGE_SCOPE);
        if (!isAdmin) {
            return false;
        }
        long activeAdmins = userRepository.countActiveByScope(USER_MANAGE_SCOPE);
        return activeAdmins <= 1;
    }

    /**
     * 记录一条用户管理审计事件。attributes 只含非敏感身份字段，绝不含临时口令或哈希。
     */
    private void audit(String eventType, AuditResult result, String actorId, String targetId,
                       Map<String, Object> attributes) {
        auditPort.record(eventType, result, actorId, targetId, null, attributes);
    }

    private LocalUser requireUser(String userId) {
        return userRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND, "user not found", Map.of()));
    }
}
