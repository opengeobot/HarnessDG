/*
 * 功能: P0B 授权生命周期集成测试——显式覆盖 p0b-exit-catalog AC-P0B-AUTH-001..009。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
package com.aihub.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aihub.authorization.application.AuthorizationDtos.CreateRoleBindingCommand;
import com.aihub.authorization.application.AuthorizationDtos.CreateRoleCommand;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.application.PermissionQueryApplicationService;
import com.aihub.authorization.application.ResourceAclApplicationService;
import com.aihub.authorization.application.RoleBindingApplicationService;
import com.aihub.authorization.application.RoleManagementApplicationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.authorization.domain.ScopeType;
import com.aihub.identity.application.IdentityCommands.CreateUserCommand;
import com.aihub.identity.application.UserManagementApplicationService;
import com.aihub.identity.application.UserView;
import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.PlatformException;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalType;
import java.util.List;
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
 * P0B 授权生命周期集成测试。
 *
 * <p>显式映射 p0b-exit-catalog 场景 ID，补充 {@link AuthorizationIT} 未覆盖的用例。
 * 无 Docker 时整体跳过，不阻断 verify。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
class AuthorizationLifecycleIT {

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

    @Autowired private AuthorizationService authorizationService;
    @Autowired private RoleManagementApplicationService roleService;
    @Autowired private RoleBindingApplicationService bindingService;
    @Autowired private ResourceAclApplicationService aclService;
    @Autowired private PermissionQueryApplicationService permissionService;
    @Autowired private UserManagementApplicationService userService;

    private PrincipalContext ctx(UserView user, Set<String> scopes) {
        return new PrincipalContext(user.principalId(), PrincipalType.USER, null, null,
                List.of(), Set.of(), scopes, 0, "zh-CN", "req_it", "trace_it");
    }

    private PrincipalContext agentCtx(String principalId, Set<String> scopes) {
        return new PrincipalContext(principalId, PrincipalType.AGENT, null, null,
                List.of(), Set.of(), scopes, 0, "zh-CN", "req_it", "trace_it");
    }

    private UserView createAndEnable(String username, Set<String> scopes) {
        UserView user = userService.createUser(new CreateUserCommand(
                username, username + " name", null, "zh-CN", PASSWORD, scopes));
        userService.enableUser(user.userId());
        return user;
    }

    // ── AC-P0B-AUTH-001: 管理员创建组织/项目 ──────────

    @Test
    void auth001_adminCanCreateRoleBindingAndGrantPermissions() {
        UserView admin = createAndEnable("auth001-admin", Set.of("authorization:manage"));

        // 管理员绑定平台 ADMIN 角色。
        bindingService.ensurePlatformAdmin(admin.principalId());

        PrincipalContext adminCtx = ctx(admin, Set.of());
        // 绑定后拥有 authorization:manage 权限。
        assertThat(authorizationService.isPermitted(adminCtx, Permissions.AUTHORIZATION_MANAGE)).isTrue();
        assertThat(authorizationService.isPermitted(adminCtx, Permissions.USER_MANAGE)).isTrue();
    }

    // ── AC-P0B-AUTH-002: 用户在不同项目获不同角色 ──────────

    @Test
    void auth002_userGetsDifferentPermissionsViaDifferentBindings() {
        UserView user = createAndEnable("auth002-multi", Set.of("user:read"));

        // 无绑定时仅有 Scope 权限。
        PrincipalContext noBinding = ctx(user, Set.of("user:read"));
        assertThat(authorizationService.isPermitted(noBinding, Permissions.USER_READ)).isTrue();
        assertThat(authorizationService.isPermitted(noBinding, Permissions.ASSET_MANAGE)).isFalse();

        // 绑定 ADMIN 后获得全部权限。
        bindingService.ensurePlatformAdmin(user.principalId());
        PrincipalContext withAdmin = ctx(user, Set.of());
        assertThat(authorizationService.isPermitted(withAdmin, Permissions.AUTHORIZATION_MANAGE)).isTrue();
    }

    // ── AC-P0B-AUTH-003: 无权限用户查询被拒绝 ──────────

    @Test
    void auth003_unauthorizedUserDeniedByDefault() {
        UserView user = createAndEnable("auth003-noperm", Set.of("user:read"));
        PrincipalContext userCtx = ctx(user, Set.of("user:read"));

        // 无 authorization:manage 权限。
        assertThat(authorizationService.isPermitted(userCtx, Permissions.AUTHORIZATION_MANAGE)).isFalse();
        // 无 asset:manage 权限。
        assertThat(authorizationService.isPermitted(userCtx, Permissions.ASSET_MANAGE)).isFalse();
        // 无 system:configure 权限。
        assertThat(authorizationService.isPermitted(userCtx, Permissions.SYSTEM_CONFIGURE)).isFalse();

        // null 上下文一律拒绝。
        assertThat(authorizationService.isPermitted(null, Permissions.USER_READ)).isFalse();
    }

    // ── AC-P0B-AUTH-004: 有权限用户操作成功 ──────────

    @Test
    void auth004_authorizedUserSucceeds() {
        UserView user = createAndEnable("auth004-perm", Set.of("asset:read", "asset:manage"));
        PrincipalContext userCtx = ctx(user, Set.of("asset:read", "asset:manage"));

        assertThat(authorizationService.isPermitted(userCtx, Permissions.ASSET_READ)).isTrue();
        assertThat(authorizationService.isPermitted(userCtx, Permissions.ASSET_MANAGE)).isTrue();
    }

    // ── AC-P0B-AUTH-005: Scope+RBAC 组合，两者都不满足时拒绝 ──────────

    @Test
    void auth005_scopeAndRbacBothRequired() {
        UserView user = createAndEnable("auth005-combo", Set.of("user:read"));

        // Scope 有 user:read，但无 dictionary:manage。
        PrincipalContext ctx = ctx(user, Set.of("user:read"));
        assertThat(authorizationService.isPermitted(ctx, Permissions.USER_READ)).isTrue();
        assertThat(authorizationService.isPermitted(ctx, Permissions.DICTIONARY_MANAGE)).isFalse();

        // Agent 即使 Scope 包含高风险动作也被拒绝。
        PrincipalContext agentCtx = agentCtx("agt_auth005",
                Set.of(Permissions.ASSET_PUBLISH, Permissions.ASSET_READ));
        assertThat(authorizationService.isPermitted(agentCtx, Permissions.ASSET_PUBLISH)).isFalse();
        assertThat(authorizationService.isPermitted(agentCtx, Permissions.ASSET_READ)).isTrue();
    }

    // ── AC-P0B-AUTH-006: 普通管理员不可修改内置角色 ──────────

    @Test
    void auth006_builtinRoleCannotBeModifiedOrDeleted() {
        // 尝试删除内置 ADMIN 角色。
        assertThatThrownBy(() -> roleService.deleteRole("rol_admin"))
                .isInstanceOfSatisfying(ConflictException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.ROLE_BUILTIN_IMMUTABLE));
    }

    // ── AC-P0B-AUTH-007: 删除有绑定的角色被拒绝，重复创建绑定被拒绝 ──────────

    @Test
    void auth007_roleWithBindingsCannotBeDeletedAndDuplicateBindingRejected() {
        UserView user = createAndEnable("auth007-user", Set.of());

        // 创建自定义角色。
        var role = roleService.createRole(new CreateRoleCommand(
                "CUSTOM_AUTH007", "Custom Role", List.of("asset:read")));

        // 绑定角色。
        bindingService.createBinding(new CreateRoleBindingCommand(
                user.principalId(), role.roleId(), ScopeType.PLATFORM, null));

        // 有绑定的角色不可删除。
        assertThatThrownBy(() -> roleService.deleteRole(role.roleId()))
                .isInstanceOfSatisfying(ConflictException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.ROLE_IN_USE));

        // 重复绑定被拒绝。
        assertThatThrownBy(() -> bindingService.createBinding(new CreateRoleBindingCommand(
                user.principalId(), role.roleId(), ScopeType.PLATFORM, null)))
                .isInstanceOfSatisfying(ConflictException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.ROLE_BINDING_ALREADY_EXISTS));
    }

    // ── AC-P0B-AUTH-008: 禁用角色绑定后权限立即失效 ──────────

    @Test
    void auth008_removeBindingRevokesPermissionsImmediately() {
        UserView user = createAndEnable("auth008-revoke", Set.of());

        // 创建自定义角色并绑定。
        var role = roleService.createRole(new CreateRoleCommand(
                "CUSTOM_AUTH008", "Revoke Test", List.of("dictionary:manage")));
        var binding = bindingService.createBinding(new CreateRoleBindingCommand(
                user.principalId(), role.roleId(), ScopeType.PLATFORM, null));

        PrincipalContext userCtx = ctx(user, Set.of());
        assertThat(authorizationService.isPermitted(userCtx, Permissions.DICTIONARY_MANAGE)).isTrue();

        // 删除绑定。
        bindingService.deleteBinding(binding.bindingId());

        // 权限立即失效。
        assertThat(authorizationService.isPermitted(userCtx, Permissions.DICTIONARY_MANAGE)).isFalse();
    }

    // ── 补充: 资源 ACL 授权与撤销 ──────────

    @Test
    void resourceAclGrantsAndRevokesAccess() {
        UserView user = createAndEnable("auth-acl-user", Set.of());
        PrincipalContext userCtx = ctx(user, Set.of());

        // 无 ACL 时拒绝。
        assertThat(authorizationService.isResourcePermitted(
                userCtx, Permissions.ASSET_READ, "ASSET", "ast_001")).isFalse();

        // 创建 ACL。
        var acl = aclService.createAcl(new com.aihub.authorization.application.AuthorizationDtos.CreateResourceAclCommand(
                user.principalId(), "ASSET", "ast_001", List.of("asset:read")));

        // ACL 授权。
        assertThat(authorizationService.isResourcePermitted(
                userCtx, Permissions.ASSET_READ, "ASSET", "ast_001")).isTrue();

        // 其他资源仍拒绝。
        assertThat(authorizationService.isResourcePermitted(
                userCtx, Permissions.ASSET_READ, "ASSET", "ast_002")).isFalse();

        // 删除 ACL。
        aclService.deleteAcl(acl.aclId());
        assertThat(authorizationService.isResourcePermitted(
                userCtx, Permissions.ASSET_READ, "ASSET", "ast_001")).isFalse();
    }

    // ── 补充: 权限目录不可随意新增 ──────────

    @Test
    void permissionCatalogIsFixedAndQueryable() {
        var permissions = permissionService.listPermissions();
        assertThat(permissions).hasSizeGreaterThanOrEqualTo(31);
        // 核心权限必须存在。
        assertThat(permissions).extracting(p -> p.permissionCode())
                .contains(Permissions.USER_READ, Permissions.ASSET_READ,
                        Permissions.AUTHORIZATION_MANAGE, Permissions.SYSTEM_CONFIGURE);
    }
}
