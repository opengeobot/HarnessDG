/*
 * 功能: DownloadStatsController Web 切片测试——授权与响应结构。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.observability.api;

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
import com.aihub.observability.application.DownloadStatsService;
import com.aihub.observability.domain.AssetDownloadStats;
import com.aihub.shared.security.IssuedToken;
import com.aihub.shared.security.TokenIssueRequest;
import com.aihub.shared.security.TokenSigner;
import com.aihub.shared.security.TokenType;
import com.aihub.shared.identity.PrincipalType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DownloadStatsController.class)
@Import({DownloadStatsController.class, PrincipalContextFilter.class, SharedKernelConfiguration.class,
        GlobalExceptionHandler.class, SecurityConfiguration.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, RefreshCookieFactory.class, AuthorizationService.class})
class DownloadStatsControllerTest {

    @Configuration
    @EnableConfigurationProperties(RefreshCookieProperties.class)
    static class TestConfig {
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokenSigner tokenSigner;

    @MockitoBean
    private DownloadStatsService downloadStatsService;
    @MockitoBean
    private RoleBindingRepository roleBindingRepository;
    @MockitoBean
    private ResourceAclRepository resourceAclRepository;
    @MockitoBean
    private AgentToolRepository agentToolRepository;

    @BeforeEach
    void stubAuthorizationRepositories() {
        given(roleBindingRepository.resolvePermissionCodes(org.mockito.ArgumentMatchers.any()))
                .willReturn(Set.of());
    }

    private String bearer(Set<String> scopes) {
        IssuedToken token = tokenSigner.issue(new TokenIssueRequest(
                "prn_user", PrincipalType.USER, 0L, scopes, TokenType.ACCESS));
        return "Bearer " + token.token();
    }

    @Test
    void assetStatsForbiddenWithoutAssetRead() throws Exception {
        mockMvc.perform(get("/api/v1/assets/ast_001/stats")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("user:read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void assetStatsOkWithAssetRead() throws Exception {
        given(downloadStatsService.getAssetStats("ast_001"))
                .willReturn(new AssetDownloadStats("ast_001", 15L, List.of()));

        mockMvc.perform(get("/api/v1/assets/ast_001/stats")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("asset:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assetId").value("ast_001"))
                .andExpect(jsonPath("$.data.totalDownloads").value(15));
    }

    @Test
    void downloadLeaderboardForbiddenWithoutSystemObserve() throws Exception {
        mockMvc.perform(get("/api/v1/system/metrics/downloads")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("asset:read"))))
                .andExpect(status().isForbidden());
    }
}
