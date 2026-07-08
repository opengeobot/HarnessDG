/*
 * 功能: 资产 REST 适配器 Web 切片测试——校验端点路由、请求体映射与响应序列化。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aihub.asset.application.AssetApplicationService;
import com.aihub.asset.application.AssetSummaryView;
import com.aihub.asset.application.AssetView;
import com.aihub.asset.domain.Asset;
import com.aihub.asset.domain.AssetStatus;
import com.aihub.asset.domain.AssetType;
import com.aihub.asset.domain.ModelProfile;
import com.aihub.asset.domain.Visibility;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.job.application.IdempotencyService;
import com.aihub.authorization.domain.AgentToolRepository;
import com.aihub.authorization.domain.ResourceAclRepository;
import com.aihub.authorization.domain.RoleBindingRepository;
import com.aihub.bootstrap.GlobalExceptionHandler;
import com.aihub.bootstrap.PrincipalContextFilter;
import com.aihub.bootstrap.SharedKernelConfiguration;
import com.aihub.bootstrap.security.RestAccessDeniedHandler;
import com.aihub.bootstrap.security.RestAuthenticationEntryPoint;
import com.aihub.bootstrap.security.SecurityConfiguration;
import com.aihub.shared.api.CursorPage;
import com.aihub.shared.identity.PrincipalType;
import com.aihub.shared.security.IssuedToken;
import com.aihub.shared.security.TokenIssueRequest;
import com.aihub.shared.security.TokenSigner;
import com.aihub.shared.security.TokenType;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * {@link AssetController} Web 切片测试。
 *
 * <p>验证 HTTP 路由、请求体反序列化与响应序列化；业务逻辑由 {@link AssetApplicationService} mock 承载。
 */
@WebMvcTest({AssetController.class, AssetCatalogController.class})
@Import({AssetController.class, AssetCatalogController.class,
        PrincipalContextFilter.class, SharedKernelConfiguration.class,
        GlobalExceptionHandler.class, SecurityConfiguration.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        AuthorizationService.class})
class AssetControllerTest {

    @Configuration
    static class TestConfig {
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TokenSigner tokenSigner;
    @MockitoBean private AssetApplicationService assetService;
    @MockitoBean private RoleBindingRepository roleBindingRepository;
    @MockitoBean private ResourceAclRepository resourceAclRepository;
    @MockitoBean private AgentToolRepository agentToolRepository;
    @MockitoBean private IdempotencyService idempotencyService;

    @BeforeEach
    void stubAuthorizationRepositories() {
        given(roleBindingRepository.resolvePermissionCodes(any())).willReturn(Set.of("asset:manage", "asset:read"));
        when(idempotencyService.execute(any(), anyString(), any())).thenAnswer(invocation -> {
            var supplier = (java.util.function.Supplier<IdempotencyService.IdempotencyResponse>) invocation.getArgument(2);
            return new IdempotencyService.IdempotencyResult(supplier.get(), false);
        });
    }

    private String bearer() {
        IssuedToken token = tokenSigner.issue(new TokenIssueRequest(
                "prn_admin", PrincipalType.USER, 0L, Set.of("asset:manage", "asset:read"), TokenType.ACCESS));
        return "Bearer " + token.token();
    }

    private AssetView modelView() {
        Asset asset = Asset.create("ast_demo", AssetType.MODEL, null, null, "nlp", "qwen-domain-7b",
                "领域问答模型", "一个领域问答模型", Visibility.INTERNAL,
                List.of("team-nlp"), List.of("text-generation"), List.of("tag_001"), "Apache-2.0",
                null,
                new ModelProfile("pytorch", "text-generation", null), null, "usr_01");
        return AssetView.from(asset);
    }

    private AssetSummaryView summaryView() {
        return new AssetSummaryView("ast_demo", AssetType.MODEL, "nlp", null, null, "qwen-domain-7b",
                "领域问答模型", "描述", Visibility.INTERNAL, AssetStatus.ACTIVE,
                List.of("team-nlp"), List.of("text-generation"), List.of("tag_001"), "Apache-2.0",
                "pytorch", "text-generation", null, null, Instant.now());
    }

    @Test
    void createAssetReturns201WithView() throws Exception {
        given(assetService.createAsset(any())).willReturn(modelView());

        String body = """
                {"type":"MODEL","namespace":"nlp","name":"qwen-domain-7b",
                 "displayName":"领域问答模型","visibility":"INTERNAL",
                 "owners":["team-nlp"],"tags":["text-generation"],"tagIds":["tag_001"],
                 "license":"Apache-2.0","model":{"framework":"pytorch","task":"text-generation"}}
                """;

        mockMvc.perform(post("/api/v1/assets")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.assetId").value("ast_demo"))
                .andExpect(jsonPath("$.data.tagIds[0]").value("tag_001"));
    }

    @Test
    void searchAssetsReturnsSummaryPage() throws Exception {
        given(assetService.searchAssets(any())).willReturn(
                new CursorPage<>(List.of(summaryView()), null, false));

        mockMvc.perform(get("/api/v1/assets")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .param("type", "MODEL")
                        .param("tagId", "tag_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].assetId").value("ast_demo"))
                .andExpect(jsonPath("$.data.items[0].tagIds[0]").value("tag_001"));
    }

    @Test
    void getAssetReturnsDetailView() throws Exception {
        given(assetService.getAsset(any(), any())).willReturn(modelView());

        mockMvc.perform(get("/api/v1/assets/ast_demo")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assetId").value("ast_demo"))
                .andExpect(jsonPath("$.data.tagIds[0]").value("tag_001"));
    }

    @Test
    void updateAssetReturnsUpdatedView() throws Exception {
        given(assetService.updateAsset(any(), any())).willReturn(modelView());

        String body = """
                {"displayName":"新名称","visibility":"PUBLIC","tagIds":["tag_001"]}
                """;

        mockMvc.perform(patch("/api/v1/assets/ast_demo")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assetId").value("ast_demo"));
    }

    @Test
    void deprecateAssetReturnsUpdatedView() throws Exception {
        given(assetService.deprecateAsset(any(), any(), any(), any(), any())).willReturn(modelView());

        mockMvc.perform(post("/api/v1/assets/ast_demo/deprecate")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assetId").value("ast_demo"));
    }

    @Test
    void archiveAssetReturnsUpdatedView() throws Exception {
        given(assetService.archiveAsset(any(), any())).willReturn(modelView());

        mockMvc.perform(post("/api/v1/assets/ast_demo/archive")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assetId").value("ast_demo"));
    }

    @Test
    void restoreAssetReturnsUpdatedView() throws Exception {
        given(assetService.restoreAsset(any(), any())).willReturn(modelView());

        mockMvc.perform(post("/api/v1/assets/ast_demo/restore")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assetId").value("ast_demo"));
    }
}