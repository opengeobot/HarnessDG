/*
 * 功能: 字典管理控制器 Web 切片测试——未授权 403、授权通过、字典项列表回显停用项。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.taxonomy.dictionary.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
import com.aihub.taxonomy.dictionary.application.DictionaryApplicationService;
import com.aihub.taxonomy.dictionary.application.DictionaryDtos.DictionaryItemView;
import com.aihub.taxonomy.dictionary.application.DictionaryDtos.DictionaryTypeView;
import com.aihub.taxonomy.domain.TaxonomyStatus;
import java.time.Instant;
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
 * 字典管理控制器 Web 切片测试。
 *
 * <p>授权判定走真实 {@link AuthorizationService}（RBAC 仓储以空集桩返回，仅靠 JWT Scope 命中）。
 */
@WebMvcTest(DictionaryController.class)
@Import({DictionaryController.class, PrincipalContextFilter.class, SharedKernelConfiguration.class,
        GlobalExceptionHandler.class, SecurityConfiguration.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, RefreshCookieFactory.class, AuthorizationService.class})
class DictionaryControllerTest {

    @Configuration
    @org.springframework.boot.context.properties.EnableConfigurationProperties(RefreshCookieProperties.class)
    static class TestConfig {
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokenSigner tokenSigner;

    @MockitoBean
    private DictionaryApplicationService dictionaryService;
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
    void listDictionariesForbiddenWhenMissingDictionaryRead() throws Exception {
        mockMvc.perform(get("/api/v1/system/dictionaries")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("user:read"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_PERMISSION_DENIED"));
    }

    @Test
    void listDictionariesOkWhenAuthorized() throws Exception {
        given(dictionaryService.listTypes()).willReturn(List.of(
                new DictionaryTypeView("license_catalog", "dict.license_catalog", 1L, TaxonomyStatus.ACTIVE)));
        mockMvc.perform(get("/api/v1/system/dictionaries")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("dictionary:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].dictCode").value("license_catalog"));
    }

    @Test
    void listItemsReturnsDisabledWhenIncludeDisabled() throws Exception {
        given(dictionaryService.listItems(anyString(), anyBoolean())).willReturn(List.of(
                new DictionaryItemView("license_catalog", "MIT", "k", 1,
                        TaxonomyStatus.DISABLED, 2L)));
        mockMvc.perform(get("/api/v1/system/dictionaries/license_catalog/items")
                        .param("includeDisabled", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("dictionary:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("DISABLED"));
    }

    @Test
    void createItemForbiddenWhenMissingDictionaryManage() throws Exception {
        mockMvc.perform(post("/api/v1/system/dictionaries/license_catalog/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("dictionary:read")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemCode\":\"GPL_3_0\",\"i18nKey\":\"k\"}"))
                .andExpect(status().isForbidden());
    }
}
