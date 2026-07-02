/*
 * 功能: AuthController / System 管理端 Web 切片测试——校验登录响应/Cookie、未认证 401、占位授权 403。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.AgentToolRepository;
import com.aihub.authorization.domain.ResourceAclRepository;
import com.aihub.authorization.domain.RoleBindingRepository;
import com.aihub.bootstrap.GlobalExceptionHandler;
import com.aihub.bootstrap.PrincipalContextFilter;
import com.aihub.bootstrap.SharedKernelConfiguration;
import com.aihub.bootstrap.security.RestAccessDeniedHandler;
import com.aihub.bootstrap.security.RestAuthenticationEntryPoint;
import com.aihub.bootstrap.security.SecurityConfiguration;
import com.aihub.identity.application.AgentManagementApplicationService;
import com.aihub.identity.application.AuthenticationApplicationService;
import com.aihub.identity.application.CurrentPrincipalView;
import com.aihub.identity.application.PrincipalQueryApplicationService;
import com.aihub.identity.application.TokenPairResult;
import com.aihub.identity.application.UserManagementApplicationService;
import com.aihub.identity.domain.LocalUserRepository;
import com.aihub.shared.identity.PrincipalType;
import com.aihub.shared.security.IssuedToken;
import com.aihub.shared.security.TokenIssueRequest;
import com.aihub.shared.security.TokenSigner;
import com.aihub.shared.security.TokenType;
import java.util.List;
import java.util.Optional;
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
 * identity 控制器 Web 切片测试。使用真实签发的 access Token 经安全过滤链建立主体上下文；
 * 授权判定走真实 {@link AuthorizationService}（合并 JWT Scope + RBAC），RBAC 仓储以空集桩返回。
 */
@WebMvcTest({AuthController.class, SystemUserController.class, SystemAgentController.class,
        SystemPrincipalController.class})
@Import({AuthController.class, SystemUserController.class, SystemAgentController.class,
        SystemPrincipalController.class,
        PrincipalContextFilter.class, SharedKernelConfiguration.class, GlobalExceptionHandler.class,
        SecurityConfiguration.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        RefreshCookieFactory.class, AuthorizationService.class})
class AuthControllerTest {

    @Configuration
    @org.springframework.boot.context.properties.EnableConfigurationProperties(RefreshCookieProperties.class)
    static class TestConfig {
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokenSigner tokenSigner;

    @MockitoBean
    private AuthenticationApplicationService authService;
    @MockitoBean
    private UserManagementApplicationService userService;
    @MockitoBean
    private AgentManagementApplicationService agentService;
    @MockitoBean
    private PrincipalQueryApplicationService principalService;
    // 强制改密拦截器在切片中未注册（无 WebMvcConfigurer）；此处仍声明以满足潜在依赖。
    @MockitoBean
    private LocalUserRepository userRepository;
    // 授权服务依赖的 RBAC/ACL/Agent 工具仓储以空集桩返回，使授权判定退化为 JWT Scope 合并规则。
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

    private TokenPairResult tokenPair() {
        CurrentPrincipalView principal = new CurrentPrincipalView("prn_1", "usr_1", PrincipalType.USER,
                "prn_1", "Alice", null, List.of(), List.of("user:read"), "zh-CN", false);
        return new TokenPairResult("access.jwt.value", "refresh.jwt.value", 900L, 604800L, principal);
    }

    private String bearer(Set<String> scopes) {
        IssuedToken token = tokenSigner.issue(new TokenIssueRequest(
                "prn_1", PrincipalType.USER, 0L, scopes, TokenType.ACCESS));
        return "Bearer " + token.token();
    }

    @Test
    void loginReturnsTokenPairAndSetsRefreshCookieWithoutBodyRefresh() throws Exception {
        given(authService.login(eq("alice"), any())).willReturn(tokenPair());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"Sup3rSecret!23\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").value("access.jwt.value"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(cookie().exists("aihub_refresh"))
                .andExpect(cookie().httpOnly("aihub_refresh", true));
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));
    }

    @Test
    void listUsersForbiddenWhenMissingScope() throws Exception {
        // 已认证但不含 user:read scope → 403。
        mockMvc.perform(get("/api/v1/system/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("asset:read"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_PERMISSION_DENIED"));
    }

    @Test
    void meReturnsCurrentPrincipalWhenAuthenticated() throws Exception {
        given(authService.currentPrincipal(any(), any())).willReturn(tokenPair().principal());
        given(userRepository.findByPrincipalId(any())).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("user:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.principalId").value("prn_1"))
                .andExpect(jsonPath("$.data.principalType").value("USER"));
    }
}
