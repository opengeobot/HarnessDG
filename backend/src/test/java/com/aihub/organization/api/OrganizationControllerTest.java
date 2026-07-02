/*
 * 功能: organization 管理控制器 Web 切片测试——未授权 403、授权通过、成员隔离防枚举 NotFound。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.organization.api;

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
import com.aihub.organization.application.OrganizationApplicationService;
import com.aihub.organization.application.OrganizationDtos.CreateOrganizationCommand;
import com.aihub.organization.application.OrganizationDtos.CreateProjectCommand;
import com.aihub.organization.application.OrganizationDtos.OrganizationMemberView;
import com.aihub.organization.application.OrganizationDtos.OrganizationView;
import com.aihub.organization.application.OrganizationDtos.ProjectView;
import com.aihub.organization.application.ProjectApplicationService;
import com.aihub.organization.domain.MemberRole;
import com.aihub.organization.domain.OrganizationStatus;
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
 * organization 管理控制器 Web 切片测试。
 *
 * <p>授权判定走真实 {@link AuthorizationService}（合并 JWT Scope + RBAC，RBAC 仓储以空集桩返回）。
 * 覆盖：缺少所需权限时 403；具备时 200/201；成员隔离防枚举（非成员访问组织项目返回 NotFound）。
 */
@WebMvcTest({OrganizationController.class, ProjectController.class})
@Import({OrganizationController.class, ProjectController.class,
        PrincipalContextFilter.class, SharedKernelConfiguration.class, GlobalExceptionHandler.class,
        SecurityConfiguration.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        RefreshCookieFactory.class, AuthorizationService.class})
class OrganizationControllerTest {

    @Configuration
    @org.springframework.boot.context.properties.EnableConfigurationProperties(RefreshCookieProperties.class)
    static class TestConfig {
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokenSigner tokenSigner;

    @MockitoBean
    private OrganizationApplicationService organizationService;
    @MockitoBean
    private ProjectApplicationService projectService;
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
    void listOrganizationsForbiddenWhenMissingProjectView() throws Exception {
        mockMvc.perform(get("/api/v1/system/organizations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("user:read"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_PERMISSION_DENIED"));
    }

    @Test
    void listOrganizationsOkWhenAuthorized() throws Exception {
        given(roleBindingRepository.resolvePermissionCodes(any())).willReturn(Set.of());
        given(organizationService.listOrganizations(anyString(), anyBoolean()))
                .willReturn(List.of(new OrganizationView("org_01", "alpha", "Alpha", null,
                        OrganizationStatus.ACTIVE, Instant.parse("2026-06-30T00:00:00Z"))));
        mockMvc.perform(get("/api/v1/system/organizations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("project:view"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].organizationId").value("org_01"))
                .andExpect(jsonPath("$.data[0].code").value("alpha"));
    }

    @Test
    void createOrganizationForbiddenWhenMissingOrganizationManage() throws Exception {
        mockMvc.perform(post("/api/v1/system/organizations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("project:view")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"alpha\",\"name\":\"Alpha\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createOrganizationCreatedWhenAuthorized() throws Exception {
        given(roleBindingRepository.resolvePermissionCodes(any())).willReturn(Set.of());
        given(organizationService.createOrganization(any(CreateOrganizationCommand.class), anyString()))
                .willReturn(new OrganizationView("org_01", "alpha", "Alpha", "gitea-alpha",
                        OrganizationStatus.ACTIVE, Instant.parse("2026-06-30T00:00:00Z")));
        mockMvc.perform(post("/api/v1/system/organizations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("organization:manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"alpha\",\"name\":\"Alpha\",\"giteaOrganization\":\"gitea-alpha\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.organizationId").value("org_01"))
                .andExpect(jsonPath("$.data.giteaOrganization").value("gitea-alpha"));
    }

    @Test
    void addMemberCreatedWhenAuthorized() throws Exception {
        given(roleBindingRepository.resolvePermissionCodes(any())).willReturn(Set.of());
        given(organizationService.addMember(any(), any(MemberRole.class), anyString()))
                .willReturn(new OrganizationMemberView("org_01", "prn_user",
                        Instant.parse("2026-06-30T00:00:00Z")));
        mockMvc.perform(post("/api/v1/system/organizations/org_01/members")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("organization:manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"principalId\":\"prn_user\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.principalId").value("prn_user"));
    }

    @Test
    void listProjectsForbiddenWhenMissingProjectView() throws Exception {
        mockMvc.perform(get("/api/v1/system/organizations/org_01/projects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("user:read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listProjectsReturnsNotFoundForNonMemberToPreventEnumeration() throws Exception {
        given(roleBindingRepository.resolvePermissionCodes(any())).willReturn(Set.of());
        // 已通过 project:view 授权，但应用层判定非成员 → 防枚举 NotFound。
        given(projectService.listProjects(anyString(), anyString(), anyBoolean()))
                .willThrow(new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND,
                        "organization not found", Map.of()));
        mockMvc.perform(get("/api/v1/system/organizations/org_01/projects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("project:view"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORGANIZATION_NOT_FOUND"));
    }

    @Test
    void createProjectCreatedWhenAuthorized() throws Exception {
        given(roleBindingRepository.resolvePermissionCodes(any())).willReturn(Set.of());
        given(projectService.createProject(any(CreateProjectCommand.class), anyString(),
                anyBoolean(), anyString()))
                .willReturn(new ProjectView("prj_01", "org_01", "alpha", "Alpha",
                        OrganizationStatus.ACTIVE, Instant.parse("2026-06-30T00:00:00Z")));
        mockMvc.perform(post("/api/v1/system/organizations/org_01/projects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("project:manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"alpha\",\"name\":\"Alpha\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.projectId").value("prj_01"));
    }
}
