/*
 * 功能: UserManagementApplicationService 单元测试——用户创建/查询/启停/重置口令。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aihub.identity.application.IdentityCommands.CreateUserCommand;
import com.aihub.identity.application.IdentityCommands.UpdateUserCommand;
import com.aihub.identity.domain.LocalUser;
import com.aihub.identity.domain.LocalUserRepository;
import com.aihub.identity.domain.PrincipalAccount;
import com.aihub.identity.domain.RefreshTokenRepository;
import com.aihub.identity.domain.UserStatus;
import com.aihub.shared.api.PageResult;
import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.shared.security.PasswordHasher;
import com.aihub.shared.security.PasswordPolicy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * UserManagementApplicationService 单元测试。
 *
 * <p>覆盖用户创建（含用户名冲突、口令策略）、列表查询、详情、更新、启用/禁用、重置口令路径。
 */
class UserManagementApplicationServiceTest {

    private LocalUserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private PasswordHasher passwordHasher;
    private PasswordPolicy passwordPolicy;
    private IdGenerator idGenerator;
    private Clock clock;
    private UserManagementApplicationService service;

    private static final Instant NOW = Instant.parse("2026-07-11T10:00:00Z");

    @BeforeEach
    void setUp() {
        userRepository = mock(LocalUserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        passwordHasher = mock(PasswordHasher.class);
        passwordPolicy = PasswordPolicy.defaults();
        idGenerator = mock(IdGenerator.class);
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new UserManagementApplicationService(
                userRepository, refreshTokenRepository, passwordHasher, passwordPolicy, idGenerator, clock);
    }

    @Test
    void createUserSuccess() {
        when(idGenerator.generate(IdPrefix.PRINCIPAL)).thenReturn("prn_001");
        when(idGenerator.generate(IdPrefix.USER)).thenReturn("usr_001");
        when(passwordHasher.hash(anyString())).thenReturn("hashed");
        when(userRepository.existsByUsername("newuser")).thenReturn(false);

        CreateUserCommand cmd = new CreateUserCommand(
                "newuser", "New User", "new@test.com", "zh-CN",
                "Strong-1a!pass", Set.of("asset.read"));

        UserView result = service.createUser(cmd);

        assertEquals("usr_001", result.userId());
        assertEquals("prn_001", result.principalId());
        assertEquals("newuser", result.username());
        verify(userRepository).create(any(PrincipalAccount.class), any(LocalUser.class));
    }

    @Test
    void createUserRejectsDuplicateUsername() {
        when(userRepository.existsByUsername("existing")).thenReturn(true);

        CreateUserCommand cmd = new CreateUserCommand(
                "existing", "Dup", "dup@test.com", "zh-CN",
                "Strong-1a!pass", Set.of());

        assertThrows(ConflictException.class, () -> service.createUser(cmd));
        verify(userRepository, never()).create(any(), any());
    }

    @Test
    void createUserRejectsWeakPassword() {
        when(userRepository.existsByUsername("weak")).thenReturn(false);

        CreateUserCommand cmd = new CreateUserCommand(
                "weak", "Weak", "weak@test.com", "zh-CN",
                "short", Set.of());

        assertThrows(ValidationException.class, () -> service.createUser(cmd));
    }

    @Test
    void createUserRejectsBlankUsername() {
        CreateUserCommand cmd = new CreateUserCommand(
                "", "No", "no@test.com", "zh-CN", "Strong-1a!pass", Set.of());

        assertThrows(ValidationException.class, () -> service.createUser(cmd));
    }

    @Test
    void listUsersDelegatesToRepository() {
        LocalUser user = buildTestUser("usr_1", "prn_1", "alice", UserStatus.ACTIVE);
        when(userRepository.search(null, null, 1, 20))
                .thenReturn(new PageResult<>(List.of(user), 1, 1, 20));

        PageResult<UserView> result = service.listUsers(null, null, 1, 20);

        assertEquals(1, result.total());
        assertEquals("alice", result.items().get(0).username());
    }

    @Test
    void getUserReturnsView() {
        LocalUser user = buildTestUser("usr_1", "prn_1", "alice", UserStatus.ACTIVE);
        when(userRepository.findByUserId("usr_1")).thenReturn(Optional.of(user));

        UserView result = service.getUser("usr_1");

        assertEquals("usr_1", result.userId());
        assertEquals("alice", result.username());
    }

    @Test
    void getUserThrowsNotFoundWhenAbsent() {
        when(userRepository.findByUserId("usr_missing")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.getUser("usr_missing"));
    }

    @Test
    void updateUserSuccess() {
        LocalUser user = buildTestUser("usr_1", "prn_1", "alice", UserStatus.ACTIVE);
        when(userRepository.findByUserId("usr_1")).thenReturn(Optional.of(user));
        when(userRepository.update(any(LocalUser.class))).thenReturn(true);

        UpdateUserCommand cmd = new UpdateUserCommand("Alice Updated", "alice2@test.com", "en-US");
        UserView result = service.updateUser("usr_1", cmd);

        assertEquals("Alice Updated", result.displayName());
        verify(userRepository).update(any(LocalUser.class));
    }

    @Test
    void enableUserSuccess() {
        LocalUser user = buildTestUser("usr_1", "prn_1", "alice", UserStatus.DISABLED);
        when(userRepository.findByUserId("usr_1")).thenReturn(Optional.of(user));
        when(userRepository.update(any(LocalUser.class))).thenReturn(true);

        service.enableUser("usr_1");

        verify(userRepository).update(any(LocalUser.class));
    }

    @Test
    void disableUserRevokesRefreshTokens() {
        LocalUser user = buildTestUser("usr_1", "prn_1", "alice", UserStatus.ACTIVE);
        when(userRepository.findByUserId("usr_1")).thenReturn(Optional.of(user));
        when(userRepository.update(any(LocalUser.class))).thenReturn(true);

        service.disableUser("usr_1");

        verify(userRepository).update(any(LocalUser.class));
        verify(refreshTokenRepository).revokeAllByPrincipal(eq("prn_1"));
    }

    @Test
    void resetPasswordSuccess() {
        LocalUser user = buildTestUser("usr_1", "prn_1", "alice", UserStatus.ACTIVE);
        when(userRepository.findByUserId("usr_1")).thenReturn(Optional.of(user));
        when(userRepository.update(any(LocalUser.class))).thenReturn(true);
        when(passwordHasher.hash(anyString())).thenReturn("new_hashed");

        service.resetPassword("usr_1", "NewStrong-1a!pass");

        verify(userRepository).update(any(LocalUser.class));
        verify(refreshTokenRepository).revokeAllByPrincipal(eq("prn_1"));
    }

    @Test
    void resetPasswordRejectsWeakPassword() {
        assertThrows(ValidationException.class,
                () -> service.resetPassword("usr_1", "short"));
        verify(userRepository, never()).update(any());
    }

    private LocalUser buildTestUser(String userId, String principalId, String username, UserStatus status) {
        return new LocalUser.Builder()
                .userId(userId)
                .principalId(principalId)
                .username(username)
                .displayName(username)
                .email(username + "@test.com")
                .locale("zh-CN")
                .passwordHash("hashed")
                .scopes(Set.of())
                .tokenVersion(0L)
                .mustChangePassword(false)
                .status(status)
                .createdAt(NOW)
                .updatedAt(NOW)
                .rowVersion(0L)
                .build();
    }
}
