/*
 * 功能: 受控标签管理控制器 Web 切片测试——未授权 403、授权创建、停用保留回显。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.taxonomy.tag.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.aihub.identity.api.RefreshCookieFactory;
import com.aihub.identity.api.RefreshCookieProperties;
import com.aihub.job.application.IdempotencyService;
import com.aihub.shared.identity.PrincipalType;
import com.aihub.shared.security.IssuedToken;
import com.aihub.shared.security.TokenIssueRequest;
import com.aihub.shared.security.TokenSigner;
import com.aihub.shared.security.TokenType;
import com.aihub.taxonomy.domain.TaxonomyStatus;
import com.aihub.taxonomy.tag.application.TagApplicationService;
import com.aihub.taxonomy.tag.application.TagDtos.TagView;
import com.aihub.taxonomy.tag.domain.TagScopeType;
import java.util.List;
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
 * 受控标签管理控制器 Web 切片测试。
 */
@WebMvcTest(TagController.class)
@Import({TagController.class, PrincipalContextFilter.class, SharedKernelConfiguration.class,
        GlobalExceptionHandler.class, SecurityConfiguration.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, RefreshCookieFactory.class, AuthorizationService.class})
class TagControllerTest {

    @Configuration
    @org.springframework.boot.context.properties.EnableConfigurationProperties(RefreshCookieProperties.class)
    static class TestConfig {
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokenSigner tokenSigner;

    @MockitoBean
    private TagApplicationService tagService;
    @MockitoBean
    private RoleBindingRepository roleBindingRepository;
    @MockitoBean
    private ResourceAclRepository resourceAclRepository;
    @MockitoBean
    private AgentToolRepository agentToolRepository;
    @MockitoBean
    private IdempotencyService idempotencyService;

    @BeforeEach
    void stubAuthorizationRepositories() {
        given(roleBindingRepository.resolvePermissionCodes(any())).willReturn(Set.of());
        given(idempotencyService.execute(any(), any(), any())).willAnswer(invocation -> {
            var supplier = (java.util.function.Supplier<IdempotencyService.IdempotencyResponse>) invocation.getArgument(2);
            return new IdempotencyService.IdempotencyResult(supplier.get(), false);
        });
    }

    private String bearer(Set<String> scopes) {
        IssuedToken token = tokenSigner.issue(new TokenIssueRequest(
                "prn_admin", PrincipalType.USER, 0L, scopes, TokenType.ACCESS));
        return "Bearer " + token.token();
    }

    @Test
    void listTagsForbiddenWhenMissingTagRead() throws Exception {
        mockMvc.perform(get("/api/v1/system/tags")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("user:read"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_PERMISSION_DENIED"));
    }

    @Test
    void listTagsOkWhenAuthorized() throws Exception {
        given(tagService.listTags(any(), any(), any(), any())).willReturn(List.of(
                new TagView("tag_1", TagScopeType.PLATFORM, "PLATFORM", "vision",
                        "视觉", "tag.vision", "#1677ff", TaxonomyStatus.ACTIVE, 1L)));
        mockMvc.perform(get("/api/v1/system/tags")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("tag:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].tagId").value("tag_1"));
    }

    @Test
    void createTagForbiddenWhenMissingTagManage() throws Exception {
        mockMvc.perform(post("/api/v1/system/tags")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("tag:read")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scopeType\":\"PLATFORM\",\"tagCode\":\"vision\","
                                + "\"displayName\":\"视觉\",\"i18nKey\":\"tag.vision\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createTagCreatedWhenAuthorized() throws Exception {
        given(tagService.createTag(any(), anyString())).willReturn(new TagView(
                "tag_1", TagScopeType.PLATFORM, "PLATFORM", "vision",
                "视觉", "tag.vision", "#1677ff", TaxonomyStatus.ACTIVE, 1L));
        mockMvc.perform(post("/api/v1/system/tags")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("tag:manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scopeType\":\"PLATFORM\",\"tagCode\":\"vision\","
                                + "\"displayName\":\"视觉\",\"i18nKey\":\"tag.vision\",\"color\":\"#1677ff\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tagId").value("tag_1"));
    }

    @Test
    void disableTagOkWhenAuthorized() throws Exception {
        mockMvc.perform(post("/api/v1/system/tags/tag_1:disable")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("tag:manage"))))
                .andExpect(status().isOk());
    }
}
