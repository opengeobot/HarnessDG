/*
 * 功能: 系统诊断 Controller Web 切片测试——验证 system:observe 权限校验、指标摘要与依赖健康返回。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.platform.observability;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.aihub.identity.api.RefreshCookieFactory;
import com.aihub.identity.api.RefreshCookieProperties;
import com.aihub.platform.observability.api.SystemDiagnosticsController;
import com.aihub.platform.observability.application.MetricsSummaryService;
import com.aihub.platform.observability.application.SystemDependencyService;
import com.aihub.platform.observability.domain.DependencyHealth;
import com.aihub.platform.observability.domain.DependencyStatus;
import com.aihub.platform.observability.domain.MetricsSummary;
import com.aihub.platform.observability.domain.SystemDependencySummary;
import com.aihub.shared.identity.PrincipalType;
import com.aihub.shared.security.IssuedToken;
import com.aihub.shared.security.TokenIssueRequest;
import com.aihub.shared.security.TokenSigner;
import com.aihub.shared.security.TokenType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link SystemDiagnosticsController} Web 切片测试。
 *
 * <p>授权判定走真实 {@link AuthorizationService}（RBAC 仓储以空集桩返回，仅靠 JWT Scope 命中）。
 * 覆盖：缺少 {@code system:observe} 时 403；具备时 200；依赖摘要不含凭据字段。
 */
@WebMvcTest(SystemDiagnosticsController.class)
@Import({SystemDiagnosticsController.class, PrincipalContextFilter.class, SharedKernelConfiguration.class,
        GlobalExceptionHandler.class, SecurityConfiguration.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, RefreshCookieFactory.class, AuthorizationService.class})
class SystemDiagnosticsControllerTest {

    @Configuration
    @EnableConfigurationProperties(RefreshCookieProperties.class)
    static class TestConfig {
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokenSigner tokenSigner;

    @MockitoBean
    private SystemDependencyService systemDependencyService;
    @MockitoBean
    private MetricsSummaryService metricsSummaryService;
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
    void metricsSummaryForbiddenWhenMissingSystemObserve() throws Exception {
        mockMvc.perform(get("/api/v1/system/metrics/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("user:read"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_PERMISSION_DENIED"));
    }

    @Test
    void metricsSummaryOkWhenAuthorized() throws Exception {
        given(metricsSummaryService.summarize()).willReturn(new MetricsSummary(
                Instant.parse("2026-07-01T10:00:00Z"),
                Map.of("http_server_requests", Map.of("count", 42)),
                Map.of("pending", 3L, "running", 1L, "dead", 0L),
                Map.of("overall", "UP")));

        mockMvc.perform(get("/api/v1/system/metrics/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("system:observe"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.generatedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.jobs.pending").value(3))
                .andExpect(jsonPath("$.data.dependencies.overall").value("UP"));
    }

    @Test
    void dependenciesForbiddenWhenMissingSystemObserve() throws Exception {
        mockMvc.perform(get("/api/v1/system/dependencies")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("user:read"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_PERMISSION_DENIED"));
    }

    @Test
    void dependenciesOkWhenAuthorizedAndNoCredentialsExposed() throws Exception {
        given(systemDependencyService.summarize()).willReturn(new SystemDependencySummary(
                DependencyHealth.UP,
                List.of(new DependencyStatus("postgres", DependencyHealth.UP, 5L))));

        mockMvc.perform(get("/api/v1/system/dependencies")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("system:observe"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.dependencies[0].name").value("postgres"))
                .andExpect(jsonPath("$.data.dependencies[0].status").value("UP"))
                .andExpect(jsonPath("$.data.dependencies[0].latencyMs").value(5))
                // 验证响应不含凭据字段
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.token").doesNotExist())
                .andExpect(jsonPath("$.data.credentials").doesNotExist());
    }
}
