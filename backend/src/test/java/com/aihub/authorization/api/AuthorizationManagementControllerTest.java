/*
 * 功能: authorization 管理控制器 Web 切片测试——未授权 403、授权通过、内置角色删除冲突 409。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.authorization.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aihub.authorization.application.AuthorizationDtos.CreateRoleCommand;
import com.aihub.authorization.application.AuthorizationDtos.RoleView;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.application.PermissionQueryApplicationService;
import com.aihub.authorization.application.ResourceAclApplicationService;
import com.aihub.authorization.application.RoleBindingApplicationService;
import com.aihub.authorization.application.RoleManagementApplicationService;
import com.aihub.authorization.domain.AgentToolRepository;
import com.aihub.authorization.domain.ResourceAclRepository;
import com.aihub.authorization.domain.RoleBindingRepository;
import com.aihub.authorization.domain.RoleType;
import com.aihub.bootstrap.GlobalExceptionHandler;
import com.aihub.bootstrap.PrincipalContextFilter;
import com.aihub.bootstrap.SharedKernelConfiguration;
import com.aihub.bootstrap.security.RestAccessDeniedHandler;
import com.aihub.bootstrap.security.RestAuthenticationEntryPoint;
import com.aihub.bootstrap.security.SecurityConfiguration;
import com.aihub.identity.api.RefreshCookieFactory;
import com.aihub.identity.api.RefreshCookieProperties;
import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.identity.PrincipalType;
import com.aihub.shared.security.IssuedToken;
import com.aihub.shared.security.TokenIssueRequest;
import com.aihub.shared.security.TokenSigner;
import com.aihub.shared.security.TokenType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * authorization 管理控制器 Web 切片测试。
 *
 * <p>授权判定走真实 {@link AuthorizationService}（合并 JWT Scope + RBAC，RBAC 仓储以空集桩返回）。
 * 覆盖：缺少 {@code authorization:read/manage} 时 403；具备时 200/201；内置角色删除返回 409 冲突。
 */
@WebMvcTest({RoleController.class, RoleBindingController.class, ResourceAclController.class})
@Import({RoleController.class, RoleBindingController.class, ResourceAclController.class,
        PrincipalContextFilter.class, SharedKernelConfiguration.class, GlobalExceptionHandler.class,
        SecurityConfiguration.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        RefreshCookieFactory.class, AuthorizationService.class})
class AuthorizationManagementControllerTest {

    @Configuration
    @org.springframework.boot.context.properties.EnableConfigurationProperties(RefreshCookieProperties.class)
    static class TestConfig {
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokenSigner tokenSigner;

    @MockitoBean
    private RoleManagementApplicationService roleService;
    @MockitoBean
    private PermissionQueryApplicationService permissionService;
    @MockitoBean
    private RoleBindingApplicationService bindingService;
    @MockitoBean
    private ResourceAclApplicationService aclService;
    @MockitoBean
    private RoleBindingRepository roleBindingRepository;
    @MockitoBean
    private ResourceAclRepository resourceAclRepository;
    @MockitoBean
    private AgentToolRepository agentToolRepository;

    @BeforeEach
    void stubAuthorizationRepositories() {
        given(roleBindingRepository.resolvePermissionCodes(any())).willReturn(Set.of());
    }

    private String bearer(Set<String> scopes) {
        IssuedToken token = tokenSigner.issue(new TokenIssueRequest(
                "prn_admin", PrincipalType.USER, 0L, scopes, TokenType.ACCESS));
        return "Bearer " + token.token();
    }

    @Test
    void listRolesForbiddenWhenMissingAuthorizationScope() throws Exception {
        mockMvc.perform(get("/api/v1/system/roles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("user:read"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_PERMISSION_DENIED"));
    }

    @Test
    void listRolesOkWhenAuthorized() throws Exception {
        given(roleService.listRoles()).willReturn(List.of(
                new RoleView("rol_admin", "ADMIN", "平台管理员", RoleType.SYSTEM,
                        List.of("authorization:manage"), 1L)));
        mockMvc.perform(get("/api/v1/system/roles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("authorization:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].roleCode").value("ADMIN"))
                .andExpect(jsonPath("$.data[0].roleType").value("SYSTEM"));
    }

    @Test
    void createRoleCreatedWhenAuthorized() throws Exception {
        given(roleService.createRole(any(CreateRoleCommand.class))).willReturn(
                new RoleView("rol_x", "VIEWER", "访客", RoleType.CUSTOM, List.of("asset:read"), 1L));
        mockMvc.perform(post("/api/v1/system/roles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("authorization:manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCode\":\"VIEWER\",\"roleName\":\"访客\","
                                + "\"permissionCodes\":[\"asset:read\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roleCode").value("VIEWER"));
    }

    @Test
    void deleteBuiltinRoleReturnsConflict() throws Exception {
        org.mockito.Mockito.doThrow(new ConflictException(ErrorCode.ROLE_BUILTIN_IMMUTABLE,
                        "builtin role cannot be deleted", Map.of()))
                .when(roleService).deleteRole("rol_admin");
        mockMvc.perform(delete("/api/v1/system/roles/rol_admin")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("authorization:manage"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_BUILTIN_IMMUTABLE"));
    }
}
