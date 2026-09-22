/*
 * 功能: AgentController Web 切片测试——授权 fail-closed、端点委托与下载 handle 脱敏。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.agent.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aihub.asset.application.AssetApplicationService;
import com.aihub.asset.application.AssetSummaryView;
import com.aihub.asset.application.AssetView;
import com.aihub.asset.domain.AssetStatus;
import com.aihub.asset.domain.AssetType;
import com.aihub.asset.domain.ProvisioningStatus;
import com.aihub.asset.domain.Visibility;
import com.aihub.audit.application.AuditService;
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
import com.aihub.platform.security.RateLimiter;
import com.aihub.shared.api.CursorPage;
import com.aihub.shared.identity.PrincipalType;
import com.aihub.shared.security.IssuedToken;
import com.aihub.shared.security.TokenIssueRequest;
import com.aihub.shared.security.TokenSigner;
import com.aihub.shared.security.TokenType;
import com.aihub.transfer.application.DownloadApplicationService;
import com.aihub.transfer.application.DownloadApplicationService.DownloadTicket;
import com.aihub.transfer.application.UploadApplicationService;
import com.aihub.transfer.application.UploadSessionView;
import com.aihub.transfer.domain.UploadSessionStatus;
import com.aihub.version.application.VersionApplicationService;
import com.aihub.version.application.VersionView;
import com.aihub.version.domain.Version;
import com.aihub.version.application.VersionQueryService;
import com.aihub.version.domain.VersionStatus;
import java.time.Instant;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AgentController.class)
@TestPropertySource(properties = "mcp.writeTools.enabled=true")
@Import({AgentController.class,
        PrincipalContextFilter.class, SharedKernelConfiguration.class, GlobalExceptionHandler.class,
        SecurityConfiguration.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        RefreshCookieFactory.class, AuthorizationService.class})
class AgentControllerTest {

    @Configuration
    @org.springframework.boot.context.properties.EnableConfigurationProperties(RefreshCookieProperties.class)
    static class TestConfig {
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokenSigner tokenSigner;

    @MockitoBean
    private AssetApplicationService assetService;
    @MockitoBean
    private VersionApplicationService versionService;
    @MockitoBean
    private VersionQueryService versionQueryService;
    @MockitoBean
    private DownloadApplicationService downloadService;
    @MockitoBean
    private UploadApplicationService uploadService;
    @MockitoBean
    private IdempotencyService idempotencyService;
    @MockitoBean
    private RateLimiter rateLimiter;
    @MockitoBean
    private AuditService auditService;
    @MockitoBean
    private RoleBindingRepository roleBindingRepository;
    @MockitoBean
    private ResourceAclRepository resourceAclRepository;
    @MockitoBean
    private AgentToolRepository agentToolRepository;

    @BeforeEach
    void stubAuthorizationRepositories() {
        given(roleBindingRepository.resolvePermissionCodes(any())).willReturn(Set.of());
        given(agentToolRepository.isToolAllowed(anyString(), anyString())).willReturn(true);
        given(rateLimiter.tryAcquire(anyString(), anyString())).willReturn(true);
    }

    private String bearer(Set<String> scopes) {
        IssuedToken token = tokenSigner.issue(new TokenIssueRequest(
                "prn_agent", PrincipalType.AGENT, 0L, scopes, TokenType.ACCESS));
        return "Bearer " + token.token();
    }

    @Test
    void searchAssetsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/agent/assets/search"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));
    }

    @Test
    void searchAssetsFailsClosedWithoutMcpInvokeScope() throws Exception {
        mockMvc.perform(get("/api/v1/agent/assets/search")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("asset:read"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_PERMISSION_DENIED"));
    }

    @Test
    void searchAssetsReturnsLatestPublished() throws Exception {
        AssetSummaryView summary = new AssetSummaryView(
                "ast_1", "aih://nlp/model/test", AssetType.MODEL, "nlp", "org_1", null,
                "test", "Test", "desc", Visibility.INTERNAL, AssetStatus.ACTIVE,
                List.of(), List.of(), List.of(), "Apache-2.0", null, null, null, null,
                Instant.now(), List.of("name"));
        Version published = new Version(
                "ver_1", "ast_1", "1.0.0", VersionStatus.PUBLISHED,
                null, null, null, Instant.now(), "usr", null,
                0L, "usr", Instant.now(), Instant.now());
        given(assetService.searchAssets(any())).willReturn(new CursorPage<>(List.of(summary), null, false));
        given(versionQueryService.findLatestPublishedByAssetIds(any())).willReturn(Map.of("ast_1", published));

        mockMvc.perform(get("/api/v1/agent/assets/search?keyword=test")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("mcp:invoke", "asset:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].latestPublished").value("1.0.0"));
    }

    @Test
    void getAssetFailsWhenAgentToolNotAllowed() throws Exception {
        given(agentToolRepository.isToolAllowed("prn_agent", "asset_get")).willReturn(false);

        mockMvc.perform(get("/api/v1/agent/assets/ast_1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("mcp:invoke", "asset:read"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MCP_TOOL_NOT_ALLOWED"));
    }

    @Test
    void getVersionDelegatesToService() throws Exception {
        VersionView view = VersionView.from(
                Version.createDraft("ver_1", "ast_1", "v1.0.0", "usr_1"));
        given(versionService.getVersion("ver_1")).willReturn(view);

        mockMvc.perform(get("/api/v1/agent/versions/ver_1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("mcp:invoke", "asset:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionId").value("ver_1"));
    }

    @Test
    void requestDownloadReturnsHandleWithoutPresignedUrl() throws Exception {
        given(downloadService.issueTicket(eq("ver_1"), eq("art_1")))
                .willReturn(new DownloadTicket("PRESIGNED_URL", "https://secret.example/obj",
                        Instant.now(), "file.bin", 42L, null, null, null, "ast_1"));

        mockMvc.perform(post("/api/v1/agent/versions/ver_1/download")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("mcp:invoke", "asset:read")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"artifactId\":\"art_1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionId").value("ver_1"))
                .andExpect(jsonPath("$.data.fileName").value("file.bin"))
                .andExpect(jsonPath("$.data.presignedUrl").doesNotExist());
    }

    @Test
    void getAssetReturnsDetail() throws Exception {
        AssetView view = new AssetView("ast_1", "aih://nlp/model/test-model", AssetType.MODEL, "nlp", "org_1", null,
                "test-model", "Test Model", "desc", Visibility.INTERNAL, AssetStatus.ACTIVE,
                List.of(), null, null, List.of(), List.of(), "Apache-2.0", null, null, null,
                ProvisioningStatus.COMPLETED, null, 0L, null, null, null,
                Instant.now(), Instant.now());
        given(assetService.getAsset(anyString(), anyString())).willReturn(view);

        mockMvc.perform(get("/api/v1/agent/assets/ast_1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("mcp:invoke", "asset:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assetId").value("ast_1"))
                .andExpect(jsonPath("$.data.coordinate").value("aih://nlp/model/test-model"));
    }

    @Test
    void createDraftVersionRequiresIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/agent/assets/ast_1/versions/draft")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("mcp:invoke", "asset:manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"0.1.0\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_ARGUMENT"));
    }

    @Test
    void createDraftVersionDelegatesToService() throws Exception {
        VersionView view = VersionView.from(
                Version.createDraft("ver_2", "ast_1", "0.1.0", "prn_agent"));
        given(idempotencyService.execute(any(), anyString(), any())).willAnswer(invocation -> {
            var supplier = (java.util.function.Supplier<IdempotencyService.IdempotencyResponse>) invocation.getArgument(2);
            return new IdempotencyService.IdempotencyResult(supplier.get(), false);
        });
        given(versionService.createDraftVersion(eq("ast_1"), eq("0.1.0"), eq("prn_agent"))).willReturn(view);

        mockMvc.perform(post("/api/v1/agent/assets/ast_1/versions/draft")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("mcp:invoke", "asset:manage")))
                        .header("Idempotency-Key", "draft-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"0.1.0\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.versionId").value("ver_2"));
    }

    @Test
    void createUploadSessionDelegatesToService() throws Exception {
        UploadSessionView session = new UploadSessionView(
                "ses_1", "ast_1", "ver_1", "prn_agent", UploadSessionStatus.OPEN,
                100L, 1, Instant.now().plusSeconds(3600), Instant.now(), null, List.of(), List.of());
        given(idempotencyService.execute(any(), anyString(), any())).willAnswer(invocation -> {
            var supplier = (java.util.function.Supplier<IdempotencyService.IdempotencyResponse>) invocation.getArgument(2);
            return new IdempotencyService.IdempotencyResult(supplier.get(), false);
        });
        given(uploadService.createSession(eq("ast_1"), eq("ver_1"), eq(100L), eq(1), eq("prn_agent")))
                .willReturn(session);

        mockMvc.perform(post("/api/v1/agent/assets/ast_1/upload-sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("mcp:invoke", "asset:manage")))
                        .header("Idempotency-Key", "upload-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionId\":\"ver_1\",\"totalBytes\":100,\"fileCount\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sessionId").value("ses_1"));
    }

    @Test
    void completeUploadSessionRequiresWriteToolAllowlist() throws Exception {
        given(agentToolRepository.isToolAllowed("prn_agent", "asset_complete_upload")).willReturn(false);

        mockMvc.perform(post("/api/v1/agent/assets/ast_1/upload-sessions/ses_1/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("mcp:invoke", "asset:manage")))
                        .header("Idempotency-Key", "complete-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MCP_TOOL_NOT_ALLOWED"));
    }

    @Test
    void getUploadSessionReturnsStatus() throws Exception {
        UploadSessionView session = new UploadSessionView(
                "ses_1", "ast_1", "ver_1", "prn_agent", UploadSessionStatus.PROCESSING,
                100L, 1, Instant.now().plusSeconds(3600), Instant.now(), "job_1", List.of(), List.of());
        given(uploadService.getSessionForAsset("ast_1", "ses_1")).willReturn(session);

        mockMvc.perform(get("/api/v1/agent/assets/ast_1/upload-sessions/ses_1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("mcp:invoke", "asset:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.materializeJobId").value("job_1"));
    }

    @Test
    void writeEndpointReturns429WhenRateLimited() throws Exception {
        given(rateLimiter.tryAcquire(eq("prn_agent"), eq("asset_create_draft"))).willReturn(false);

        mockMvc.perform(post("/api/v1/agent/assets/ast_1/versions/draft")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("mcp:invoke", "asset:manage")))
                        .header("Idempotency-Key", "draft-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"0.1.0\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }
}
