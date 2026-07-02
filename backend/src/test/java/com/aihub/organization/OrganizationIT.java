/*
 * 功能: organization 集成测试——以 Testcontainers PostgreSQL 验证 V5 迁移、成员关系落库、
 *       跨组织隔离与 AccessScope 经组织成员关系接通。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.AccessScope;
import com.aihub.authorization.domain.Permissions;
import com.aihub.identity.application.IdentityCommands.CreateUserCommand;
import com.aihub.identity.application.UserManagementApplicationService;
import com.aihub.identity.application.UserView;
import com.aihub.organization.application.OrganizationApplicationService;
import com.aihub.organization.application.OrganizationDtos.AddMemberCommand;
import com.aihub.organization.application.OrganizationDtos.CreateOrganizationCommand;
import com.aihub.organization.application.OrganizationDtos.CreateProjectCommand;
import com.aihub.organization.application.OrganizationDtos.OrganizationMemberView;
import com.aihub.organization.application.OrganizationDtos.OrganizationView;
import com.aihub.organization.application.OrganizationDtos.ProjectView;
import com.aihub.organization.application.ProjectApplicationService;
import com.aihub.organization.domain.MemberRole;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.aihub.shared.identity.PrincipalType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * organization 集成测试。无 Docker 时整体跳过，不阻断 verify。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
class OrganizationIT {

    private static final String PASSWORD = "Sup3rSecret!23";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("aihub")
                    .withUsername("aihub")
                    .withPassword("aihub");

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private OrganizationApplicationService organizationService;
    @Autowired
    private ProjectApplicationService projectService;
    @Autowired
    private AuthorizationService authorizationService;
    @Autowired
    private UserManagementApplicationService userService;

    private PrincipalContext userContext(UserView user, Set<String> scopes) {
        return new PrincipalContext(user.principalId(), PrincipalType.USER, null, null,
                List.of(), Set.of(), scopes, 0, "zh-CN", "req_it", "trace_it");
    }

    @Test
    void v5MigrationAndMembershipPersistenceAndIsolation() {
        // V5 迁移已执行：创建组织/项目/成员可落库。
        UserView member = userService.createUser(new CreateUserCommand(
                "org_member_it", "OrgMember", null, "zh-CN", PASSWORD, Set.of("project:view")));
        userService.enableUser(member.userId());

        OrganizationView orgA = organizationService.createOrganization(
                new CreateOrganizationCommand("org-alpha-it", "Alpha IT", null), "system");
        OrganizationView orgB = organizationService.createOrganization(
                new CreateOrganizationCommand("org-beta-it", "Beta IT", null), "system");

        // 添加成员到 orgA（验证 principal 存在性 + 成员关系落库）。
        organizationService.addMember(
                new AddMemberCommand(orgA.organizationId(), member.principalId()),
                MemberRole.MEMBER, "system");
        List<OrganizationMemberView> members = organizationService.listMembers(orgA.organizationId());
        assertThat(members).extracting(OrganizationMemberView::principalId)
                .contains(member.principalId());

        // 重复添加 → 冲突。
        assertThatThrownBy(() -> organizationService.addMember(
                new AddMemberCommand(orgA.organizationId(), member.principalId()),
                MemberRole.MEMBER, "system")).isInstanceOf(com.aihub.shared.error.ConflictException.class);

        // 在 orgA 创建项目（成员可见）。
        ProjectView project = projectService.createProject(
                new CreateProjectCommand(orgA.organizationId(), "proj-alpha-it", "Alpha Project"),
                member.principalId(), false, "system");
        assertThat(project.organizationId()).isEqualTo(orgA.organizationId());

        // 成员隔离：成员可见 orgA 项目，不可见 orgB（防枚举 NotFound）。
        assertThat(projectService.listProjects(orgA.organizationId(), member.principalId(), false))
                .isNotEmpty();
        assertThatThrownBy(() -> projectService.listProjects(
                orgB.organizationId(), member.principalId(), false))
                .isInstanceOf(NotFoundException.class);

        // 平台管理员无需成员关系即可访问 orgB。
        assertThat(projectService.listProjects(orgB.organizationId(), member.principalId(), true))
                .isEmpty();

        // 移除成员后，再访问 orgA 项目 → NotFound。
        organizationService.removeMember(orgA.organizationId(), member.principalId(), "system");
        assertThatThrownBy(() -> projectService.listProjects(
                orgA.organizationId(), member.principalId(), false))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void accessScopeIncludesMembershipOrganizationIds() {
        UserView member = userService.createUser(new CreateUserCommand(
                "scope_member_it", "ScopeMember", null, "zh-CN", PASSWORD, Set.of("project:view")));
        userService.enableUser(member.userId());
        OrganizationView org = organizationService.createOrganization(
                new CreateOrganizationCommand("org-scope-it", "Scope IT", null), "system");
        organizationService.addMember(
                new AddMemberCommand(org.organizationId(), member.principalId()),
                MemberRole.MEMBER, "system");

        PrincipalContextHolder.set(userContext(member, Set.of("project:view")));
        try {
            AccessScope scope = authorizationService.computeAccessScope("ASSET", Permissions.ASSET_READ);
            // 非平台管理员，但组织成员关系应纳入 AccessScope.organizationIds（接通 organization 出站端口）。
            assertThat(scope.platformAdmin()).isFalse();
            assertThat(scope.organizationIds()).contains(org.organizationId());
        } finally {
            PrincipalContextHolder.clear();
        }
    }
}
