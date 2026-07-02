/*
 * 功能: 配置管理控制器 Web 切片测试——未授权 403、授权更新、Secret 入库被拒映射。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.configuration.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.aihub.configuration.application.ConfigurationApplicationService;
import com.aihub.configuration.application.ConfigurationDtos.ConfigView;
import com.aihub.configuration.domain.ConfigValueType;
import com.aihub.identity.api.RefreshCookieFactory;
import com.aihub.identity.api.RefreshCookieProperties;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.ValidationException;
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
 * 配置管理控制器 Web 切片测试。
 */
@WebMvcTest(ConfigurationController.class)
@Import({ConfigurationController.class, PrincipalContextFilter.class, SharedKernelConfiguration.class,
        GlobalExceptionHandler.class, SecurityConfiguration.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, RefreshCookieFactory.class, AuthorizationService.class})
class ConfigurationControllerTest {

    @Configuration
    @org.springframework.boot.context.properties.EnableConfigurationProperties(RefreshCookieProperties.class)
    static class TestConfig {
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokenSigner tokenSigner;

    @MockitoBean
    private ConfigurationApplicationService configurationService;
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
    void listConfigurationsForbiddenWhenMissingSystemConfigure() throws Exception {
        mockMvc.perform(get("/api/v1/system/configurations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("system:observe"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_PERMISSION_DENIED"));
    }

    @Test
    void listConfigurationsOkWhenAuthorized() throws Exception {
        given(configurationService.listConfigurations()).willReturn(List.of(
                new ConfigView("job.dvc.maxRetries", ConfigValueType.INTEGER, 5, true, 1L)));
        mockMvc.perform(get("/api/v1/system/configurations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("system:configure"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].configKey").value("job.dvc.maxRetries"))
                .andExpect(jsonPath("$.data[0].value").value(5));
    }

    @Test
    void updateConfigurationForbiddenWhenMissingSystemConfigure() throws Exception {
        mockMvc.perform(put("/api/v1/system/configurations/job.dvc.maxRetries")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("system:observe")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":9,\"expectedVersion\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateConfigurationMapsSecretRejectionToBadRequest() throws Exception {
        given(configurationService.updateConfiguration(anyString(), any(), anyString()))
                .willThrow(new ValidationException(ErrorCode.CONFIG_SECRET_FORBIDDEN,
                        "secret-bearing configuration key is forbidden",
                        Map.of("configKey", "app.password")));
        mockMvc.perform(put("/api/v1/system/configurations/app.password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("system:configure")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"x\",\"expectedVersion\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONFIG_SECRET_FORBIDDEN"));
    }

    @Test
    void updateConfigurationOkWhenAuthorized() throws Exception {
        given(configurationService.updateConfiguration(anyString(), any(), anyString()))
                .willReturn(new ConfigView("job.dvc.maxRetries", ConfigValueType.INTEGER, 9, true, 2L));
        mockMvc.perform(put("/api/v1/system/configurations/job.dvc.maxRetries")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("system:configure")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":9,\"expectedVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.value").value(9))
                .andExpect(jsonPath("$.data.version").value(2));
    }
}
