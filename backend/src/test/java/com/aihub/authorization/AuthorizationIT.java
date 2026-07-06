/*
 * 功能: authorization 集成测试——以 Testcontainers PostgreSQL 验证 V4 迁移预置数据、
 *       角色绑定经 RBAC 解析授予权限、默认拒绝与 Agent 高风险动作拒绝。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.application.PermissionQueryApplicationService;
import com.aihub.authorization.application.RoleBindingApplicationService;
import com.aihub.authorization.application.RoleManagementApplicationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.identity.application.IdentityCommands.CreateUserCommand;
import com.aihub.identity.application.UserManagementApplicationService;
import com.aihub.identity.application.UserView;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalType;
import java.lang.reflect.Field;
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
 * authorization 集成测试。无 Docker 时整体跳过，不阻断 verify。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
class AuthorizationIT {

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
    private AuthorizationService authorizationService;
    @Autowired
    private RoleManagementApplicationService roleService;
    @Autowired
    private PermissionQueryApplicationService permissionService;
    @Autowired
    private RoleBindingApplicationService bindingService;
    @Autowired
    private UserManagementApplicationService userService;

    private PrincipalContext userContext(UserView user, Set<String> scopes) {
        return new PrincipalContext(user.principalId(), PrincipalType.USER, null, null,
                List.of(), Set.of(), scopes, 0, "zh-CN", "req_it", "trace_it");
    }

    private PrincipalContext agentContext(String principalId, Set<String> scopes) {
        return new PrincipalContext(principalId, PrincipalType.AGENT, null, null,
                List.of(), Set.of(), scopes, 0, "zh-CN", "req_it", "trace_it");
    }

    @Test
    void presetPermissionsAndRolesExist() {
        assertThat(permissionService.listPermissions()).hasSizeGreaterThanOrEqualTo(35);
        assertThat(roleService.listRoles())
                .extracting(view -> view.roleCode())
                .contains("ADMIN", "READER", "ASSET_AUTHOR", "OBSERVER");
    }

    /**
     * TASK-P0BR-003：验证 Permissions.java 常量与数据库 iam_permission seed 无漂移。
     */
    @Test
    void permissionsJavaConstantsMatchDatabaseSeed() throws Exception {
        Set<String> dbCodes = permissionService.listPermissions().stream()
                .map(v -> v.permissionCode())
                .collect(java.util.stream.Collectors.toSet());

        // 反射收集 Permissions.java 中所有 String 常量
        Set<String> javaConstants = new java.util.LinkedHashSet<>();
        for (Field field : Permissions.class.getDeclaredFields()) {
            if (field.getType() == String.class
                    && java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    && java.lang.reflect.Modifier.isFinal(field.getModifiers())
                    && !field.getName().equals("HIGH_RISK_ACTIONS")) {
                javaConstants.add((String) field.get(null));
            }
        }

        // 每个 Java 常量必须在数据库 seed 中存在
        for (String code : javaConstants) {
            assertThat(dbCodes)
                    .as("Permissions.java 常量 '%s' 必须在 iam_permission seed 中存在", code)
                    .contains(code);
        }
    }

    @Test
    void roleBindingGrantsPermissionViaRbacAndDeniesByDefault() {
        UserView alice = userService.createUser(new CreateUserCommand(
                "alice_auth", "Alice", null, "zh-CN", PASSWORD, Set.of("user:read")));
        userService.enableUser(alice.userId());

        // 未绑定 ADMIN 前：仅有 JWT Scope user:read，无 authorization:manage。
        PrincipalContext before = userContext(alice, Set.of("user:read"));
        assertThat(authorizationService.isPermitted(before, Permissions.USER_READ)).isTrue();
        assertThat(authorizationService.isPermitted(before, Permissions.AUTHORIZATION_MANAGE)).isFalse();

        // 绑定 ADMIN 后：RBAC 解析出 authorization:manage（即便 Scope 为空也命中）。
        bindingService.ensurePlatformAdmin(alice.principalId());
        PrincipalContext after = userContext(alice, Set.of());
        assertThat(authorizationService.isPermitted(after, Permissions.AUTHORIZATION_MANAGE)).isTrue();
        assertThat(authorizationService.isPermitted(after, Permissions.ASSET_PUBLISH)).isTrue();
    }

    @Test
    void agentHighRiskActionDeniedEvenWhenScoped() {
        PrincipalContext agent = agentContext("agt_it", Set.of(Permissions.ASSET_PUBLISH));
        assertThat(authorizationService.isPermitted(agent, Permissions.ASSET_PUBLISH)).isFalse();
        assertThat(authorizationService.isPermitted(agent, Permissions.ASSET_READ)).isTrue();
    }
}
