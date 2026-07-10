/*
 * 功能: 资产 ACL REST 适配器 Web 切片测试。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.asset.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aihub.asset.application.AssetApplicationService;
import com.aihub.asset.application.AssetView;
import com.aihub.asset.domain.Asset;
import com.aihub.asset.domain.AssetType;
import com.aihub.asset.domain.ModelProfile;
import com.aihub.asset.domain.Visibility;
import com.aihub.authorization.application.AuthorizationDtos.ResourceAclView;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.application.ResourceAclApplicationService;
import com.aihub.authorization.domain.AgentToolRepository;
import com.aihub.authorization.domain.ResourceAclRepository;
import com.aihub.authorization.domain.RoleBindingRepository;
import com.aihub.bootstrap.GlobalExceptionHandler;
import com.aihub.bootstrap.PrincipalContextFilter;
import com.aihub.bootstrap.SharedKernelConfiguration;
import com.aihub.bootstrap.security.RestAccessDeniedHandler;
import com.aihub.bootstrap.security.RestAuthenticationEntryPoint;
import com.aihub.bootstrap.security.SecurityConfiguration;
import com.aihub.job.application.IdempotencyService;
import com.aihub.shared.identity.PrincipalType;
import com.aihub.shared.security.IssuedToken;
import com.aihub.shared.security.TokenIssueRequest;
import com.aihub.shared.security.TokenSigner;
import com.aihub.shared.security.TokenType;
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

@WebMvcTest(AssetAccessController.class)
@Import({AssetAccessController.class, PrincipalContextFilter.class, SharedKernelConfiguration.class,
        GlobalExceptionHandler.class, SecurityConfiguration.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        AuthorizationService.class})
class AssetAccessControllerTest {

    @Configuration
    static class TestConfig {
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenSigner tokenSigner;
    @MockitoBean private ResourceAclApplicationService aclService;
    @MockitoBean private AssetApplicationService assetService;
    @MockitoBean private RoleBindingRepository roleBindingRepository;
    @MockitoBean private ResourceAclRepository resourceAclRepository;
    @MockitoBean private AgentToolRepository agentToolRepository;
    @MockitoBean private IdempotencyService idempotencyService;

    @BeforeEach
    void stubAuth() {
        given(roleBindingRepository.resolvePermissionCodes(any()))
                .willReturn(Set.of("asset:manage", "asset:read"));
        when(idempotencyService.execute(any(), any(), any())).thenAnswer(invocation -> {
            var supplier = (java.util.function.Supplier<IdempotencyService.IdempotencyResponse>) invocation.getArgument(2);
            return new IdempotencyService.IdempotencyResult(supplier.get(), false);
        });
        Asset asset = Asset.create("ast_demo", AssetType.MODEL, null, null, "nlp", "demo",
                "Demo", null, Visibility.INTERNAL, List.of(), List.of(), null, "Apache-2.0",
                "team_01", new ModelProfile("pytorch", "text-generation", null), null, "usr_01");
        given(assetService.getAsset(eq("ast_demo"), any())).willReturn(AssetView.from(asset));
    }

    private String bearer() {
        IssuedToken token = tokenSigner.issue(new TokenIssueRequest(
                "prn_admin", PrincipalType.USER, 0L, Set.of("asset:manage", "asset:read"), TokenType.ACCESS));
        return "Bearer " + token.token();
    }

    @Test
    void listAssetAccessReturns200() throws Exception {
        given(aclService.listAclsByResource("ASSET", "ast_demo")).willReturn(List.of(
                new ResourceAclView("acl_01", "prn_user", "ASSET", "ast_demo",
                        List.of("asset:read"), Instant.now())));

        mockMvc.perform(get("/api/v1/assets/ast_demo/access")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].aclId").value("acl_01"));
    }

    @Test
    void createAssetAccessReturns201() throws Exception {
        given(aclService.createAcl(any())).willReturn(
                new ResourceAclView("acl_new", "prn_user", "ASSET", "ast_demo",
                        List.of("asset:read"), Instant.now()));

        mockMvc.perform(post("/api/v1/assets/ast_demo/access")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"principalId":"prn_user","permissionCodes":["asset:read"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.aclId").value("acl_new"));
    }

    @Test
    void deleteAssetAccessReturns200() throws Exception {
        mockMvc.perform(delete("/api/v1/assets/ast_demo/access/acl_01")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk());
    }
}
