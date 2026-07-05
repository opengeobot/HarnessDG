/*
 * 功能: Team 管理控制器 Web 切片测试——未授权 403、授权通过、NotFound 防枚举。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
package com.aihub.organization.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.aihub.identity.api.RefreshCookieFactory;
import com.aihub.identity.api.RefreshCookieProperties;
import com.aihub.job.application.IdempotencyService;
import com.aihub.organization.application.TeamApplicationService;
import com.aihub.organization.application.TeamDtos.TeamMemberView;
import com.aihub.organization.application.TeamDtos.TeamView;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
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
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Team 管理控制器 Web 切片测试。
 *
 * <p>覆盖：缺少权限 403；具备权限时 200/201；Team 不存在 NotFound。
 */
@WebMvcTest(TeamController.class)
@Import({TeamController.class,
        PrincipalContextFilter.class, SharedKernelConfiguration.class, GlobalExceptionHandler.class,
        SecurityConfiguration.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        RefreshCookieFactory.class, AuthorizationService.class})
class TeamControllerTest {

    @Configuration
    @org.springframework.boot.context.properties.EnableConfigurationProperties(RefreshCookieProperties.class)
    static class TestConfig {
    }

    private static final Instant NOW = Instant.parse("2026-07-05T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokenSigner tokenSigner;

    @MockitoBean
    private TeamApplicationService teamService;
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
        org.mockito.BDDMockito.given(idempotencyService.execute(any(), any(), any())).willAnswer(invocation -> {
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
    void listTeamsForbiddenWhenMissingProjectView() throws Exception {
        mockMvc.perform(get("/api/v1/system/organizations/org_01/teams")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("user:read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listTeamsOkWhenAuthorized() throws Exception {
        given(teamService.listTeams("org_01"))
                .willReturn(List.of(new TeamView("team_01", "org_01", "Alpha",
                        "desc", "ACTIVE", NOW)));
        mockMvc.perform(get("/api/v1/system/organizations/org_01/teams")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("project:view"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].teamId").value("team_01"));
    }

    @Test
    void createTeamForbiddenWhenMissingTeamManage() throws Exception {
        mockMvc.perform(post("/api/v1/system/organizations/org_01/teams")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("project:view")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alpha\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createTeamCreatedWhenAuthorized() throws Exception {
        given(teamService.createTeam(any(), anyString()))
                .willReturn(new TeamView("team_01", "org_01", "Alpha",
                        "desc", "ACTIVE", NOW));
        mockMvc.perform(post("/api/v1/system/organizations/org_01/teams")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("team:manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alpha\",\"description\":\"desc\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.teamId").value("team_01"));
    }

    @Test
    void getTeamReturnsNotFoundWhenMissing() throws Exception {
        given(teamService.getTeam("team_ghost"))
                .willThrow(new NotFoundException(ErrorCode.TEAM_NOT_FOUND,
                        "team not found", Map.of()));
        mockMvc.perform(get("/api/v1/system/organizations/org_01/teams/team_ghost")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("project:view"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
    }

    @Test
    void updateTeamOkWhenAuthorized() throws Exception {
        given(teamService.updateTeam(anyString(), any(), anyString()))
                .willReturn(new TeamView("team_01", "org_01", "Beta",
                        "new desc", "ACTIVE", NOW));
        mockMvc.perform(put("/api/v1/system/organizations/org_01/teams/team_01")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("team:manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Beta\",\"description\":\"new desc\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Beta"));
    }

    @Test
    void addMemberCreatedWhenAuthorized() throws Exception {
        given(teamService.addMember(any(), anyString()))
                .willReturn(new TeamMemberView("team_01", "prn_user", "MEMBER", NOW));
        mockMvc.perform(post("/api/v1/system/organizations/org_01/teams/team_01/members")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("team:manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"principalId\":\"prn_user\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.principalId").value("prn_user"));
    }

    @Test
    void removeMemberOkWhenAuthorized() throws Exception {
        mockMvc.perform(delete("/api/v1/system/organizations/org_01/teams/team_01/members/prn_user")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("team:manage"))))
                .andExpect(status().isOk());
    }
}
