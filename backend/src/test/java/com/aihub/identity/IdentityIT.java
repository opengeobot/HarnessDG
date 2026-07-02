/*
 * 功能: identity 集成测试——以 Testcontainers PostgreSQL 验证登录/锁定/禁用即时失效/刷新轮换/
 *       重放被拒/改密失效/凭据交换/Agent 禁用等核心链路。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aihub.identity.application.AgentManagementApplicationService;
import com.aihub.identity.application.AuthenticationApplicationService;
import com.aihub.identity.application.CreatedAgentResult;
import com.aihub.identity.application.IdentityCommands.CreateAgentCommand;
import com.aihub.identity.application.IdentityCommands.CreateUserCommand;
import com.aihub.identity.application.TokenPairResult;
import com.aihub.identity.application.UserManagementApplicationService;
import com.aihub.identity.application.UserView;
import com.aihub.shared.error.AuthenticationException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.PlatformException;
import com.aihub.shared.security.JwtClaims;
import com.aihub.shared.security.TokenRevocationChecker;
import com.aihub.shared.security.TokenVerifier;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * identity 集成测试。无 Docker 时整体跳过，不阻断 verify。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
class IdentityIT {

    private static final String PASSWORD = "Sup3rSecret!23";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("aihub")
                    .withUsername("aihub")
                    .withPassword("aihub");

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private AuthenticationApplicationService authService;
    @Autowired
    private UserManagementApplicationService userService;
    @Autowired
    private AgentManagementApplicationService agentService;
    @Autowired
    private TokenVerifier tokenVerifier;
    @Autowired
    private TokenRevocationChecker revocationChecker;

    private UserView createActiveUser(String username) {
        UserView created = userService.createUser(new CreateUserCommand(
                username, username + " name", null, "zh-CN", PASSWORD, Set.of("user:read")));
        // 创建用户处于 PENDING_ACTIVATION + mustChangePassword；改密激活以便后续登录路径稳定。
        userService.enableUser(created.userId());
        return created;
    }

    @Test
    void loginSucceedsRefreshRotatesAndReplayIsRejected() {
        createActiveUser("alice");
        TokenPairResult login = authService.login("alice", PASSWORD);
        assertThat(login.accessToken()).isNotBlank();
        assertThat(login.refreshToken()).isNotBlank();

        // 刷新轮换：旧 refresh 轮换为新令牌对。
        TokenPairResult refreshed = authService.refresh(login.refreshToken());
        assertThat(refreshed.refreshToken()).isNotBlank();
        assertThat(refreshed.refreshToken()).isNotEqualTo(login.refreshToken());

        // 重放旧 refresh：判定重放并吊销整族。
        assertThatThrownBy(() -> authService.refresh(login.refreshToken()))
                .isInstanceOfSatisfying(PlatformException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.AUTH_REFRESH_REPLAYED));

        // 整族吊销后，刚轮换出的 refresh 也不可用。
        assertThatThrownBy(() -> authService.refresh(refreshed.refreshToken()))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void wrongPasswordLocksAccountAfterThreshold() {
        createActiveUser("bob");
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> authService.login("bob", "WrongPass!234"))
                    .isInstanceOfSatisfying(PlatformException.class,
                            ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));
        }
        // 第 5 次失败触发锁定。
        assertThatThrownBy(() -> authService.login("bob", "WrongPass!234"))
                .isInstanceOfSatisfying(PlatformException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.AUTH_ACCOUNT_LOCKED));
        // 锁定后即使口令正确也被拒。
        assertThatThrownBy(() -> authService.login("bob", PASSWORD))
                .isInstanceOfSatisfying(PlatformException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.AUTH_ACCOUNT_LOCKED));
    }

    @Test
    void disableUserRevokesAccessImmediately() {
        UserView user = createActiveUser("carol");
        TokenPairResult login = authService.login("carol", PASSWORD);
        JwtClaims accessClaims = tokenVerifier.verify(login.accessToken());
        assertThat(revocationChecker.isRevoked(accessClaims)).isFalse();

        userService.disableUser(user.userId());
        // 禁用后 token_version 递增 + 主体禁用 → 旧 access 立即失效。
        assertThat(revocationChecker.isRevoked(accessClaims)).isTrue();
        assertThatThrownBy(() -> authService.login("carol", PASSWORD))
                .isInstanceOfSatisfying(PlatformException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.AUTH_ACCOUNT_DISABLED));
    }

    @Test
    void changePasswordInvalidatesOldAccessToken() {
        UserView user = createActiveUser("dave");
        TokenPairResult login = authService.login("dave", PASSWORD);
        JwtClaims oldAccess = tokenVerifier.verify(login.accessToken());
        assertThat(revocationChecker.isRevoked(oldAccess)).isFalse();

        authService.changePassword(user.principalId(), PASSWORD, "N3wStr0ngPass!9");
        // 改密后旧 access（旧 tokenVersion）失效。
        assertThat(revocationChecker.isRevoked(oldAccess)).isTrue();
        // 旧 refresh 也被吊销。
        assertThatThrownBy(() -> authService.refresh(login.refreshToken()))
                .isInstanceOf(AuthenticationException.class);
        // 新口令可登录。
        assertThat(authService.login("dave", "N3wStr0ngPass!9").accessToken()).isNotBlank();
    }

    @Test
    void duplicateUsernameRejected() {
        createActiveUser("erin");
        assertThatThrownBy(() -> userService.createUser(new CreateUserCommand(
                "erin", "dup", null, "zh-CN", PASSWORD, Set.of())))
                .isInstanceOfSatisfying(PlatformException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.USER_ALREADY_EXISTS));
    }

    @Test
    void agentCredentialExchangeAndDisable() {
        CreatedAgentResult created = agentService.registerAgent(new CreateAgentCommand(
                "openclaw", "openclaw", "internal", 2, Set.of("asset:read")));
        String agentId = created.agent().agentId();
        assertThat(created.credential()).isNotBlank();

        TokenPairResult token = authService.exchangeClientCredential(agentId, created.credential());
        assertThat(token.accessToken()).isNotBlank();
        // Agent 凭据交换默认不签发刷新令牌。
        assertThat(token.refreshToken()).isNull();

        // 错误凭据被拒。
        assertThatThrownBy(() -> authService.exchangeClientCredential(agentId, "bad-credential"))
                .isInstanceOfSatisfying(PlatformException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));

        // 禁用后凭据交换失败。
        agentService.disableAgent(agentId);
        assertThatThrownBy(() -> authService.exchangeClientCredential(agentId, created.credential()))
                .isInstanceOfSatisfying(PlatformException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.AUTH_ACCOUNT_DISABLED));
    }
}
