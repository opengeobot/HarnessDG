/*
 * 功能: 发布审批 REST 适配器 Web 切片测试。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
package com.aihub.version.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.aihub.shared.identity.PrincipalType;
import com.aihub.shared.security.IssuedToken;
import com.aihub.shared.security.TokenIssueRequest;
import com.aihub.shared.security.TokenSigner;
import com.aihub.shared.security.TokenType;
import com.aihub.version.application.PublishApplicationService;
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
 * {@link ReviewController} Web 切片测试。
 */
@WebMvcTest(ReviewController.class)
@Import({ReviewController.class,
        PrincipalContextFilter.class, SharedKernelConfiguration.class,
        GlobalExceptionHandler.class, SecurityConfiguration.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        AuthorizationService.class})
class ReviewControllerTest {

    @Configuration
    static class TestConfig {
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenSigner tokenSigner;
    @MockitoBean private PublishApplicationService publishApplicationService;
    @MockitoBean private RoleBindingRepository roleBindingRepository;
    @MockitoBean private ResourceAclRepository resourceAclRepository;
    @MockitoBean private AgentToolRepository agentToolRepository;

    @BeforeEach
    void stubAuthorizationRepositories() {
        given(roleBindingRepository.resolvePermissionCodes(any())).willReturn(Set.of("version:manage", "version:read"));
    }

    private String bearer() {
        IssuedToken token = tokenSigner.issue(new TokenIssueRequest(
                "prn_test", PrincipalType.USER, 0L, Set.of("version:manage", "version:read"), TokenType.ACCESS));
        return "Bearer " + token.token();
    }

    @Test
    void submitPublishRequestReturns200() throws Exception {
        given(publishApplicationService.submitPublishRequest(anyString())).willReturn("pbr_001");

        mockMvc.perform(post("/api/v1/versions/ver_001/publish-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("pbr_001"));
    }

    @Test
    void submitDecisionReturns200() throws Exception {
        doNothing().when(publishApplicationService).submitDecision(anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/v1/publish-requests/pbr_001/decisions")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVE","comments":"LGTM"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("submitted"));
    }

    @Test
    void deprecateVersionReturns200() throws Exception {
        doNothing().when(publishApplicationService).deprecateVersion(anyString());

        mockMvc.perform(post("/api/v1/versions/ver_001/deprecate")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("deprecated"));
    }

    @Test
    void archiveVersionReturns200() throws Exception {
        doNothing().when(publishApplicationService).archiveVersion(anyString());

        mockMvc.perform(post("/api/v1/versions/ver_001/archive")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("archived"));
    }
}
