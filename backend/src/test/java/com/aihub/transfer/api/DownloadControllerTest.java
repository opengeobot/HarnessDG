/*
 * 功能: DownloadController Web 切片测试——签发下载票据（含授权检查与版本不存在）。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
package com.aihub.transfer.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.identity.PrincipalType;
import com.aihub.shared.security.IssuedToken;
import com.aihub.shared.security.TokenIssueRequest;
import com.aihub.shared.security.TokenSigner;
import com.aihub.shared.security.TokenType;
import com.aihub.transfer.application.DownloadApplicationService;
import com.aihub.transfer.application.DownloadApplicationService.DownloadTicket;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * DownloadController Web 切片测试。
 */
@WebMvcTest(DownloadController.class)
@Import({DownloadController.class,
        PrincipalContextFilter.class, SharedKernelConfiguration.class, GlobalExceptionHandler.class,
        SecurityConfiguration.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        RefreshCookieFactory.class})
class DownloadControllerTest {

    @Configuration
    @org.springframework.boot.context.properties.EnableConfigurationProperties(RefreshCookieProperties.class)
    static class TestConfig {
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokenSigner tokenSigner;

    @MockitoBean
    private DownloadApplicationService downloadService;

    private String bearer(Set<String> scopes) {
        IssuedToken token = tokenSigner.issue(new TokenIssueRequest(
                "prn_user", PrincipalType.USER, 0L, scopes, TokenType.ACCESS));
        return "Bearer " + token.token();
    }

    @Test
    void issueDownloadTicketWithArtifactId() throws Exception {
        Instant expires = Instant.parse("2026-07-05T12:00:00Z");
        given(downloadService.issueTicket(eq("ver_01"), eq("art_01")))
                .willReturn(new DownloadTicket("PRESIGNED_URL", "https://minio/dvc-cache/obj",
                        expires, "model.bin", 1024L));
        mockMvc.perform(get("/api/v1/versions/ver_01/download")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("asset:read")))
                        .param("artifactId", "art_01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("PRESIGNED_URL"))
                .andExpect(jsonPath("$.presignedUrl").value("https://minio/dvc-cache/obj"));
    }

    @Test
    void issueDownloadTicketWithoutArtifactIdReturnsDvc() throws Exception {
        given(downloadService.issueTicket(eq("ver_01"), isNull()))
                .willReturn(new DownloadTicket("GIT_DVC", null, null, null, null));
        mockMvc.perform(get("/api/v1/versions/ver_01/download")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("asset:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("GIT_DVC"));
    }

    @Test
    void issueDownloadTicketReturnsNotFoundWhenVersionMissing() throws Exception {
        given(downloadService.issueTicket(eq("ver_ghost"), isNull()))
                .willThrow(new NotFoundException(ErrorCode.VERSION_NOT_FOUND,
                        "version not found", Map.of("versionId", "ver_ghost")));
        mockMvc.perform(get("/api/v1/versions/ver_ghost/download")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("asset:read"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VERSION_NOT_FOUND"));
    }
}
