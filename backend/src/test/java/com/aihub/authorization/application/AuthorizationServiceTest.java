/*
 * 功能: AuthorizationService 单元测试——默认拒绝、Scope 命中、RBAC 命中、ACL 命中、Agent 高风险拒绝、防枚举 NotFound。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.authorization.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.aihub.authorization.domain.AgentToolRepository;
import com.aihub.authorization.domain.Permissions;
import com.aihub.authorization.domain.ResourceAclRepository;
import com.aihub.authorization.domain.RoleBindingRepository;
import com.aihub.shared.error.AuthorizationException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * AuthorizationService 单元测试。
 *
 * <p>覆盖默认拒绝、JWT Scope 命中、RBAC 命中、资源 ACL 命中、Agent 高风险动作拒绝与防资源枚举 NotFound。
 */
class AuthorizationServiceTest {

    private RoleBindingRepository roleBindingRepository;
    private ResourceAclRepository resourceAclRepository;
    private AgentToolRepository agentToolRepository;
    private AuthorizationService service;

    @BeforeEach
    void setUp() {
        roleBindingRepository = mock(RoleBindingRepository.class);
        resourceAclRepository = mock(ResourceAclRepository.class);
        agentToolRepository = mock(AgentToolRepository.class);
        service = new AuthorizationService(roleBindingRepository, resourceAclRepository, agentToolRepository);
        lenient().when(roleBindingRepository.resolvePermissionCodes(eq("prn_user")))
                .thenReturn(Set.of());
    }

    private PrincipalContext user(String principalId, Set<String> scopes) {
        return new PrincipalContext(principalId, PrincipalType.USER, null, null, List.of(),
                Set.of(), scopes, 0, "zh-CN", "req_1", "trace_1");
    }

    private PrincipalContext agent(String principalId, Set<String> scopes) {
        return new PrincipalContext(principalId, PrincipalType.AGENT, null, null, List.of(),
                Set.of(), scopes, 0, "zh-CN", "req_1", "trace_1");
    }

    @Test
    void isPermittedReturnsFalseWhenContextIsNull() {
        assertFalse(service.isPermitted(null, Permissions.USER_READ));
    }

    @Test
    void isPermittedDeniesByDefaultWhenNoScopeAndNoRole() {
        assertFalse(service.isPermitted(user("prn_user", Set.of()), Permissions.USER_READ));
    }

    @Test
    void isPermittedAllowsWhenJwtScopeContainsPermission() {
        assertTrue(service.isPermitted(user("prn_user", Set.of(Permissions.USER_READ)), Permissions.USER_READ));
    }

    @Test
    void isPermittedAllowsWhenRbacResolvesPermission() {
        lenient().when(roleBindingRepository.resolvePermissionCodes(eq("prn_user")))
                .thenReturn(Set.of(Permissions.AUTHORIZATION_MANAGE));
        PrincipalContext ctx = user("prn_user", Set.of("asset:read"));
        assertTrue(service.isPermitted(ctx, Permissions.AUTHORIZATION_MANAGE));
    }

    @Test
    void agentIsDeniedForHighRiskActionEvenWithScope() {
        PrincipalContext ctx = agent("prn_agent", Set.of(Permissions.ASSET_PUBLISH));
        assertFalse(service.isPermitted(ctx, Permissions.ASSET_PUBLISH));
    }

    @Test
    void agentIsDeniedForSystemConfigureEvenWithRbac() {
        lenient().when(roleBindingRepository.resolvePermissionCodes(eq("prn_agent")))
                .thenReturn(Set.of(Permissions.SYSTEM_CONFIGURE));
        PrincipalContext ctx = agent("prn_agent", Set.of());
        assertFalse(service.isPermitted(ctx, Permissions.SYSTEM_CONFIGURE));
    }

    @Test
    void requirePermissionThrowsAuthorizationExceptionWhenDenied() {
        AuthorizationException ex = assertThrows(AuthorizationException.class,
                () -> service.requirePermission(user("prn_user", Set.of()), Permissions.USER_MANAGE));
        org.junit.jupiter.api.Assertions.assertEquals(ErrorCode.AUTH_PERMISSION_DENIED, ex.errorCode());
    }

    @Test
    void requirePermissionPassesWhenScopeMatches() {
        assertDoesNotThrow(() ->
                service.requirePermission(user("prn_user", Set.of(Permissions.USER_READ)), Permissions.USER_READ));
    }

    @Test
    void isResourcePermittedFallsBackToAclWhenGlobalDenied() {
        PrincipalContext ctx = user("prn_user", Set.of());
        lenient().when(resourceAclRepository.hasPermission("ASSET", "ast_1", "prn_user", Permissions.ASSET_READ))
                .thenReturn(true);
        assertTrue(service.isResourcePermitted(ctx, Permissions.ASSET_READ, "ASSET", "ast_1"));
    }

    @Test
    void isResourcePermittedDeniesWhenNeitherGlobalNorAcl() {
        PrincipalContext ctx = user("prn_user", Set.of());
        lenient().when(resourceAclRepository.hasPermission("ASSET", "ast_1", "prn_user", Permissions.ASSET_READ))
                .thenReturn(false);
        assertFalse(service.isResourcePermitted(ctx, Permissions.ASSET_READ, "ASSET", "ast_1"));
    }

    @Test
    void authorizeResourceOrNotFoundThrowsNotFoundWhenDenied() {
        PrincipalContext ctx = user("prn_user", Set.of());
        lenient().when(resourceAclRepository.hasPermission("ASSET", "ast_1", "prn_user", Permissions.ASSET_READ))
                .thenReturn(false);
        // 上下文通过 ThreadLocal 持有。
        com.aihub.shared.identity.PrincipalContextHolder.set(ctx);
        try {
            assertThrows(NotFoundException.class,
                    () -> service.authorizeResourceOrNotFound(
                            Permissions.ASSET_READ, "ASSET", "ast_1", ErrorCode.ASSET_NOT_FOUND));
        } finally {
            com.aihub.shared.identity.PrincipalContextHolder.clear();
        }
    }

    @Test
    void requireToolAllowedThrowsWhenAgentToolNotInAllowlist() {
        lenient().when(agentToolRepository.isToolAllowed("agt_1", "asset.search")).thenReturn(false);
        assertThrows(AuthorizationException.class,
                () -> service.requireToolAllowed("agt_1", "asset.search"));
    }
}
