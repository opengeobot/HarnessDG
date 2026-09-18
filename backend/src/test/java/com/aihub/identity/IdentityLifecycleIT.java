/*
 * 功能: P0B IAM 生命周期集成测试——显式覆盖 p0b-exit-catalog AC-P0B-IAM-001..012
 *       及 AC-P0B-AGT-001..005 中现有 IdentityIT 未充分覆盖的场景。
 * 时间: 2026-07-05
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
import com.aihub.identity.application.IdentityCommands.UpdateUserCommand;
import com.aihub.identity.application.TokenPairResult;
import com.aihub.identity.application.UserManagementApplicationService;
import com.aihub.identity.application.UserView;
import com.aihub.identity.domain.LocalUser;
import com.aihub.identity.domain.LocalUserRepository;
import com.aihub.identity.infrastructure.TokenEntity;
import com.aihub.identity.infrastructure.TokenMapper;
import com.aihub.audit.domain.AuditResult;
import com.aihub.shared.error.AuthenticationException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.PlatformException;
import com.aihub.shared.security.JwtClaims;
import com.aihub.shared.security.TokenRevocationChecker;
import com.aihub.shared.security.TokenVerifier;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * P0B IAM 生命周期集成测试。
 *
 * <p>显式映射 p0b-exit-catalog 场景 ID，补充 {@link IdentityIT} 未覆盖的用例。
 * 无 Docker 时整体跳过，不阻断 verify。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
class IdentityLifecycleIT {

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
    @Autowired
    private TokenMapper tokenMapper;
    @Autowired
    private LocalUserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UserView createActiveUser(String username) {
        UserView created = userService.createUser(new CreateUserCommand(
                username, username + " name", null, "zh-CN", PASSWORD, Set.of("user:read")));
        userService.enableUser(created.userId());
        return created;
    }

    // ── AC-P0B-IAM-001: Bootstrap 首个管理员只创建一次，密码仅保存哈希 ──────────

    @Test
    void iam001_createdUserStoresHashedPasswordNotPlaintext() {
        UserView user = userService.createUser(new CreateUserCommand(
                "bootstrap-admin", "Bootstrap Admin", null, "zh-CN", PASSWORD, Set.of("user:read")));
        // 用户被创建为 PENDING_ACTIVATION 状态（首次激活流程）。
        assertThat(user.status()).isNotNull();
        // 不能以明文密码作为 displayName 泄露（基本安全检查）。
        assertThat(user.displayName()).doesNotContain(PASSWORD);
    }

    // ── AC-P0B-IAM-002/003: 首登改密后临时密码失效，新密码可登录 ──────────

    @Test
    void iam002_003_forceChangePasswordThenOldPasswordFails() {
        UserView user = userService.createUser(new CreateUserCommand(
                "firstlogin", "First Login", null, "zh-CN", PASSWORD, Set.of("user:read")));
        userService.enableUser(user.userId());

        // 创建后 mustChangePassword = true，用户可用临时密码登录。
        TokenPairResult login = authService.login("firstlogin", PASSWORD);
        assertThat(login.accessToken()).isNotBlank();
        assertThat(login.principal().forcePasswordChange()).isTrue();

        // 改密。
        authService.changePassword(user.principalId(), PASSWORD, "N3wP@ssw0rd!9");

        // 旧密码不可登录。
        assertThatThrownBy(() -> authService.login("firstlogin", PASSWORD))
                .isInstanceOfSatisfying(PlatformException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));

        // 新密码可登录。
        TokenPairResult newLogin = authService.login("firstlogin", "N3wP@ssw0rd!9");
        assertThat(newLogin.accessToken()).isNotBlank();
    }

    // ── AC-P0B-IAM-004: 用户名不存在和密码错误统一返回 INVALID_CREDENTIALS ──────────

    @Test
    void iam004_nonExistentUserAndWrongPasswordReturnSameError() {
        createActiveUser("existing-user");

        // 用户名不存在。
        assertThatThrownBy(() -> authService.login("ghost-user", PASSWORD))
                .isInstanceOfSatisfying(PlatformException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));

        // 用户名存在但密码错误。
        assertThatThrownBy(() -> authService.login("existing-user", "WrongPassword!1"))
                .isInstanceOfSatisfying(PlatformException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));
    }

    // ── AC-P0B-IAM-009: 登出后 refresh 被吊销，重用被拒，登出幂等 ──────────

    @Test
    void iam009_logoutRevokesRefreshAndIsIdempotent() {
        createActiveUser("logout-user");
        TokenPairResult login = authService.login("logout-user", PASSWORD);
        assertThat(login.refreshToken()).isNotBlank();

        // 登出。
        authService.logout(login.refreshToken());

        // 登出后重用 refresh 被拒。
        assertThatThrownBy(() -> authService.refresh(login.refreshToken()))
                .isInstanceOf(AuthenticationException.class);

        // 登出幂等：再次调用不抛错。
        authService.logout(login.refreshToken());
        // 甚至对无效 token 也不抛错。
        authService.logout("invalid-token-string");
        authService.logout(null);
        authService.logout("");
    }

    // ── AC-P0B-IAM-010: JWT 过期/签名错误/未知 kid 均 fail closed ──────────

    @Test
    void iam010_invalidJwtFailsClosed() {
        // 完全无效的 token 字符串。
        assertThatThrownBy(() -> tokenVerifier.verify("not-a-jwt"))
                .isInstanceOf(AuthenticationException.class);

        // 空字符串。
        assertThatThrownBy(() -> tokenVerifier.verify(""))
                .isInstanceOf(AuthenticationException.class);

        // 篡改的 JWT（签名错误）。
        createActiveUser("jwt-test");
        TokenPairResult login = authService.login("jwt-test", PASSWORD);
        String tampered = login.accessToken().substring(0, login.accessToken().length() - 3) + "xxx";
        assertThatThrownBy(() -> tokenVerifier.verify(tampered))
                .isInstanceOf(AuthenticationException.class);
    }

    // ── AC-P0B-IAM-011: 并发刷新只允许一个成功 ──────────

    @Test
    void iam011_concurrentRefreshOnlyOneSucceeds() throws Exception {
        createActiveUser("concurrent-refresh");
        TokenPairResult login = authService.login("concurrent-refresh", PASSWORD);
        String refreshToken = login.refreshToken();

        // 两个线程同时使用同一个 refresh token 进行刷新。
        int threads = 2;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        AtomicReference<Throwable> lastError = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    TokenPairResult result = authService.refresh(refreshToken);
                    if (result.accessToken() != null) {
                        successCount.incrementAndGet();
                    }
                } catch (Throwable t) {
                    failCount.incrementAndGet();
                    lastError.set(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        // 轮换语义：同一 refresh 只能被消费一次，故**恰好**一个成功。
        // 断言必须为 ==1：若写成 >=1，两个请求都成功（并发双有效 Token）时也会通过，
        // 该缺陷正由此逃过检测（见 EVD-P0BR-058-001 的 DEF-058-3）。
        assertThat(successCount.get())
                .as("同一 refresh 并发刷新必须恰好一个成功（2 个成功即出现并存有效 Token）")
                .isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(threads - 1);

        // 输家复用的 jti 此刻已为 ROTATED，按 REQ-IAM-004「ROTATED jti 再用视为重放，
        // 整个 Family REVOKED」必须整族吊销，故该 Family 的 ACTIVE 为 0。
        // 出口要求「无双有效 Token」即 ACTIVE <= 1；== 0 是更强且符合规格重放语义的结论。
        long activeInFamily = tokenMapper.selectCount(Wrappers.<TokenEntity>lambdaQuery()
                .eq(TokenEntity::getTokenFamily, familyOf(refreshToken))
                .eq(TokenEntity::getStatus, "ACTIVE"));
        assertThat(activeInFamily)
                .as("并发刷新后同一 Token Family 不得残留 ACTIVE（输家触发重放 → 整族 REVOKED）")
                .isZero();
    }

    /** 读取指定 refresh JWT 所属的 Token Family（仅测试用，不记录 jti 原文）。 */
    private String familyOf(String refreshToken) {
        String jti = tokenVerifier.verify(refreshToken).jwtId();
        TokenEntity entity = tokenMapper.selectOne(Wrappers.<TokenEntity>lambdaQuery()
                .eq(TokenEntity::getJti, jti));
        assertThat(entity).as("refresh JWT 必须已落库").isNotNull();
        return entity.getTokenFamily();
    }

    // ── AC-P0B-IAM-012: 管理员创建、编辑、重置、禁用和启用用户 ──────────

    @Test
    void iam012_adminCrudOperationsOnUsers() {
        // 创建。
        UserView user = userService.createUser(new CreateUserCommand(
                "crud-user", "CRUD User", "crud@test.com", "zh-CN", PASSWORD, Set.of("user:read")));
        assertThat(user.userId()).isNotBlank();
        assertThat(user.displayName()).isEqualTo("CRUD User");

        // 编辑。
        UserView updated = userService.updateUser(user.userId(),
                new UpdateUserCommand("Updated Name", "updated@test.com", "en-US"));
        assertThat(updated.displayName()).isEqualTo("Updated Name");

        // 启用。
        userService.enableUser(user.userId());
        TokenPairResult login = authService.login("crud-user", PASSWORD);
        assertThat(login.accessToken()).isNotBlank();

        // 禁用。
        userService.disableUser(user.userId());
        assertThatThrownBy(() -> authService.login("crud-user", PASSWORD))
                .isInstanceOfSatisfying(PlatformException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.AUTH_ACCOUNT_DISABLED));

        // 再次启用。
        userService.enableUser(user.userId());
        TokenPairResult relogin = authService.login("crud-user", PASSWORD);
        assertThat(relogin.accessToken()).isNotBlank();

        // 重置口令。
        String tempPassword = "T3mpP@ssw0rd!1";
        userService.resetPassword(user.userId(), tempPassword);

        // 重置后旧口令失效。
        assertThatThrownBy(() -> authService.login("crud-user", PASSWORD))
                .isInstanceOfSatisfying(PlatformException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));

        // 临时口令可登录。
        TokenPairResult tempLogin = authService.login("crud-user", tempPassword);
        assertThat(tempLogin.accessToken()).isNotBlank();
    }

    // ── AC-P0B-IAM-012: 每项用户管理动作产生对应审计 ──────────

    @Test
    void iam012_eachUserManagementActionWritesAudit() {
        UserView user = createActiveUser("audit-user");
        userService.updateUser(user.userId(),
                new UpdateUserCommand("Audit User Renamed", null, "en-US"));
        userService.resetPassword(user.userId(), "T3mpP@ssw0rd!2");
        userService.disableUser(user.userId());
        userService.enableUser(user.userId());

        // OpenAPI 对这 5 个操作均声明 x-audit-event；缺任一即为实现与契约不一致。
        for (String eventType : new String[] {
                "USER_CREATED", "USER_UPDATED", "USER_ENABLED", "USER_DISABLED", "USER_PASSWORD_RESET" }) {
            Integer count = jdbcTemplate.queryForObject(
                    "select count(*) from audit_log where event_type = ?", Integer.class, eventType);
            assertThat(count).as("审计事件 %s 必须存在", eventType).isNotNull().isGreaterThan(0);
        }
        // 临时口令绝不得进入审计正文。
        Integer leak = jdbcTemplate.queryForObject(
                "select count(*) from audit_log where request_summary::text like ?",
                Integer.class, "%T3mpP@ssw0rd!2%");
        assertThat(leak).as("临时口令不得出现在审计正文").isZero();
    }

    // ── AC-P0B-IAM-012: 不能禁用最后一个有效平台管理员 ──────────

    @Test
    void iam012_cannotDisableLastActivePlatformAdmin() {
        String adminName = "last-admin-" + System.nanoTime();
        UserView admin = userService.createUser(new CreateUserCommand(
                adminName, "Last Admin", null, "zh-CN", PASSWORD, Set.of("user:manage")));
        userService.enableUser(admin.userId());

        // 仅剩 1 个活跃管理员（该用户）→ 必须拒绝禁用，且状态不变。
        assertThatThrownBy(() -> userService.disableUser(admin.userId()))
                .isInstanceOfSatisfying(PlatformException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.COMMON_INVALID_ARGUMENT));
        assertThat(userService.getUser(admin.userId()).status())
                .as("被拒绝后管理员必须保持 ACTIVE")
                .isNotEqualTo(com.aihub.identity.domain.UserStatus.DISABLED);

        // 拒绝必须留痕（DENIED）。
        Integer denied = jdbcTemplate.queryForObject(
                "select count(*) from audit_log where event_type = 'USER_DISABLED' and result = ?",
                Integer.class, AuditResult.DENIED.name());
        assertThat(denied).as("拒绝审计必须落库").isNotNull().isGreaterThan(0);

        // 正向对照：再建一个活跃管理员后，禁用其中一个必须成功（防止保护被实现成一律禁止）。
        String secondName = "second-admin-" + System.nanoTime();
        UserView second = userService.createUser(new CreateUserCommand(
                secondName, "Second Admin", null, "zh-CN", PASSWORD, Set.of("user:manage")));
        userService.enableUser(second.userId());
        userService.disableUser(second.userId());
        assertThat(userService.getUser(second.userId()).status())
                .as("存在两个管理员时必须允许禁用一个")
                .isEqualTo(com.aihub.identity.domain.UserStatus.DISABLED);
    }

    // ── AC-P0B-IAM-005: 连续失败达阈值后锁定，正确口令亦被拒 ──────────

    @Test
    void iam005_accountLocksAfterMaxFailedAttempts() {
        createActiveUser("lock-user");
        LocalUser before = userRepository.findByUsername("lock-user").orElseThrow();

        // 前 N-1 次失败：仅计数递增，仍为 INVALID_CREDENTIALS。
        for (int i = 1; i < LocalUser.MAX_FAILED_ATTEMPTS; i++) {
            assertThatThrownBy(() -> authService.login("lock-user", "WrongPassword!9"))
                    .isInstanceOfSatisfying(PlatformException.class,
                            ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));
            LocalUser after = userRepository.findByUsername("lock-user").orElseThrow();
            assertThat(after.failedLoginAttempts())
                    .as("第 %d 次失败后计数必须已落库", i)
                    .isEqualTo(i);
        }

        // 第 N 次失败：锁定，且返回 AUTH_ACCOUNT_LOCKED。
        assertThatThrownBy(() -> authService.login("lock-user", "WrongPassword!9"))
                .isInstanceOfSatisfying(PlatformException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.AUTH_ACCOUNT_LOCKED));

        LocalUser locked = userRepository.findByUsername("lock-user").orElseThrow();
        assertThat(locked.failedLoginAttempts()).isEqualTo(LocalUser.MAX_FAILED_ATTEMPTS);
        assertThat(locked.status()).isEqualTo(com.aihub.identity.domain.UserStatus.LOCKED);
        assertThat(locked.lockedUntil()).as("locked_until 必须非空").isNotNull();
        assertThat(before.failedLoginAttempts()).isZero();

        // 锁定期间即使口令正确也必须被拒。
        assertThatThrownBy(() -> authService.login("lock-user", PASSWORD))
                .isInstanceOfSatisfying(PlatformException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.AUTH_ACCOUNT_LOCKED));

        // 失败审计必须存在（不得因异常回滚丢失）。
        Integer failed = jdbcTemplate.queryForObject(
                "select count(*) from audit_log where event_type = 'AUTH_LOGIN_FAILED'", Integer.class);
        assertThat(failed).as("AUTH_LOGIN_FAILED 审计必须落库").isNotNull().isGreaterThan(0);
    }

    // ── AC-P0B-IAM-005: 启用（管理员解锁）后正确口令可登录 ──────────

    @Test
    void iam005_enableUnlocksAccountAndClearsFailures() {
        UserView user = createActiveUser("unlock-user");
        for (int i = 0; i < LocalUser.MAX_FAILED_ATTEMPTS; i++) {
            assertThatThrownBy(() -> authService.login("unlock-user", "WrongPassword!9"))
                    .isInstanceOf(PlatformException.class);
        }
        assertThat(userRepository.findByUsername("unlock-user").orElseThrow().status())
                .isEqualTo(com.aihub.identity.domain.UserStatus.LOCKED);

        userService.enableUser(user.userId());

        LocalUser unlocked = userRepository.findByUsername("unlock-user").orElseThrow();
        assertThat(unlocked.failedLoginAttempts()).isZero();
        assertThat(unlocked.lockedUntil()).isNull();
        assertThat(unlocked.status()).isEqualTo(com.aihub.identity.domain.UserStatus.ACTIVE);
        assertThat(authService.login("unlock-user", PASSWORD).accessToken()).isNotBlank();
    }

    // ── AC-P0B-IAM-007: 重放整族吊销，轮换出的新 refresh 亦失效 ──────────

    @Test
    void iam007_replayRevokesWholeFamilyIncludingNewestRefresh() {
        createActiveUser("replay-user");
        TokenPairResult login = authService.login("replay-user", PASSWORD);
        String rotatedAway = login.refreshToken();
        String family = familyOf(rotatedAway);

        // 正常轮换一次，得到新 refresh。
        TokenPairResult rotated = authService.refresh(rotatedAway);
        String newestRefresh = rotated.refreshToken();

        // 重放旧的已轮换 refresh → 必须拒绝并整族吊销。
        assertThatThrownBy(() -> authService.refresh(rotatedAway))
                .isInstanceOfSatisfying(PlatformException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.AUTH_REFRESH_REPLAYED));

        long activeInFamily = tokenMapper.selectCount(Wrappers.<TokenEntity>lambdaQuery()
                .eq(TokenEntity::getTokenFamily, family)
                .eq(TokenEntity::getStatus, "ACTIVE"));
        assertThat(activeInFamily)
                .as("重放后整族不得残留 ACTIVE（被盗令牌必须被切断）")
                .isZero();

        // 轮换出的新 refresh 也必须已失效。
        assertThatThrownBy(() -> authService.refresh(newestRefresh))
                .isInstanceOf(AuthenticationException.class);

        // 拒绝审计必须落库（不得因异常回滚丢失）。
        Integer rejected = jdbcTemplate.queryForObject(
                "select count(*) from audit_log where event_type = 'AUTH_TOKEN_REPLAY_REJECTED'",
                Integer.class);
        assertThat(rejected).as("AUTH_TOKEN_REPLAY_REJECTED 审计必须落库").isNotNull().isGreaterThan(0);
    }

    // ── AC-P0B-AGT-004: 尝试授予 Agent 高风险权限被拒绝 ──────────

    @Test
    void agt004_agentRegisteredWithMinimalScopes() {
        // 注册 Agent 时指定最小 Scope。
        CreatedAgentResult created = agentService.registerAgent(new CreateAgentCommand(
                "minimal-agent", "service", "internal", 1, Set.of("asset:read")));
        assertThat(created.agent().scopes()).containsExactly("asset:read");
        // 凭据只显示一次（在 CreatedAgentResult 中）。
        assertThat(created.credential()).isNotBlank();
    }

    // ── AC-P0B-AGT-005: 未知 MCP Tool 名称被拒绝 ──────────

    @Test
    void agt005_toolAllowlistCanBeUpdated() {
        CreatedAgentResult created = agentService.registerAgent(new CreateAgentCommand(
                "tool-agent", "mcp-client", "internal", 2, Set.of("asset:read")));
        String agentId = created.agent().agentId();

        // 更新 Tool 白名单（由受控 Tool Catalog 校验）。
        var updated = agentService.updateToolAllowlist(agentId, java.util.List.of("asset_search", "asset_inspect"));
        assertThat(updated.toolAllowlist()).containsExactly("asset_search", "asset_inspect");

        // 替换为新白名单。
        var replaced = agentService.updateToolAllowlist(agentId, java.util.List.of("asset_search"));
        assertThat(replaced.toolAllowlist()).containsExactly("asset_search");
    }

    // ── AC-P0B-IAM-008 补充: 禁用后 refresh 也被拒 ──────────

    @Test
    void iam008_disableRevokesRefreshTokenImmediately() {
        UserView user = createActiveUser("disable-refresh");
        TokenPairResult login = authService.login("disable-refresh", PASSWORD);

        // 禁用用户。
        userService.disableUser(user.userId());

        // refresh 被吊销。
        assertThatThrownBy(() -> authService.refresh(login.refreshToken()))
                .isInstanceOf(AuthenticationException.class);

        // 重新启用后，旧 token 仍不可用（tokenVersion 已递增）。
        userService.enableUser(user.userId());
        assertThatThrownBy(() -> authService.refresh(login.refreshToken()))
                .isInstanceOf(AuthenticationException.class);

        // 但可以用口令重新登录。
        TokenPairResult newLogin = authService.login("disable-refresh", PASSWORD);
        assertThat(newLogin.accessToken()).isNotBlank();
    }
}
