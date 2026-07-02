/*
 * 功能: 用户管理应用服务，编排用户创建、查询、更新、启停与重置口令用例。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.application;

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
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户管理应用服务。
 *
 * <p>管理端用例编排：创建用户（同时建立主体）、列表/详情、更新资料、启用/禁用（即时失效）、
 * 重置口令（强制下次修改）。临时口令明文绝不写日志或审计正文。
 */
@Service
public class UserManagementApplicationService {

    private final LocalUserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicy passwordPolicy;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public UserManagementApplicationService(LocalUserRepository userRepository,
                                            RefreshTokenRepository refreshTokenRepository,
                                            PasswordHasher passwordHasher,
                                            PasswordPolicy passwordPolicy,
                                            IdGenerator idGenerator,
                                            Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
        this.idGenerator = idGenerator;
        this.clock = clock;
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
    }

    /**
     * 禁用用户并吊销其全部刷新令牌（access 由 tokenVersion 递增自动失效）。
     */
    @Transactional
    public void disableUser(String userId) {
        LocalUser user = requireUser(userId);
        user.disable(clock.instant());
        userRepository.update(user);
        refreshTokenRepository.revokeAllByPrincipal(user.principalId());
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
    }

    private LocalUser requireUser(String userId) {
        return userRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND, "user not found", Map.of()));
    }
}
