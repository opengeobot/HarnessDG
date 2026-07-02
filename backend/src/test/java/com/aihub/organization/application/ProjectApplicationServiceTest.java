/*
 * 功能: ProjectApplicationService 单元测试——组织可达性校验/防枚举与项目创建冲突。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.organization.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import com.aihub.organization.application.OrganizationDtos.CreateProjectCommand;
import com.aihub.organization.application.OrganizationDtos.ProjectView;
import com.aihub.organization.domain.OrganizationMemberRepository;
import com.aihub.organization.domain.OrganizationRepository;
import com.aihub.organization.domain.Project;
import com.aihub.organization.domain.ProjectRepository;
import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * ProjectApplicationService 单元测试。
 *
 * <p>覆盖组织可达性校验（成员隔离/防枚举：非成员访问返回 ORGANIZATION_NOT_FOUND）、
 * 平台管理员放行、项目编码冲突与成功创建。
 */
class ProjectApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-30T00:00:00Z");

    private ProjectRepository projectRepository;
    private OrganizationRepository organizationRepository;
    private OrganizationMemberRepository memberRepository;
    private IdGenerator idGenerator;
    private AuditPort auditPort;
    private ProjectApplicationService service;

    @BeforeEach
    void setUp() {
        projectRepository = Mockito.mock(ProjectRepository.class);
        organizationRepository = Mockito.mock(OrganizationRepository.class);
        memberRepository = Mockito.mock(OrganizationMemberRepository.class);
        idGenerator = Mockito.mock(IdGenerator.class);
        auditPort = Mockito.mock(AuditPort.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new ProjectApplicationService(projectRepository, organizationRepository,
                memberRepository, idGenerator, auditPort, clock);
        lenient().when(idGenerator.generate(IdPrefix.PROJECT)).thenReturn("prj_01");
    }

    @Test
    void listProjectsNotFoundWhenOrganizationMissing() {
        given(organizationRepository.existsByOrganizationId("org_x")).willReturn(false);
        NotFoundException ex = assertThrows(NotFoundException.class, () ->
                service.listProjects("org_x", "prn_user", false));
        assertEquals(ErrorCode.ORGANIZATION_NOT_FOUND, ex.errorCode());
    }

    @Test
    void listProjectsNotFoundForNonMemberToPreventEnumeration() {
        given(organizationRepository.existsByOrganizationId("org_a")).willReturn(true);
        given(memberRepository.exists("org_a", "prn_user")).willReturn(false);
        // 非成员访问他人组织项目 → 与"组织不存在"同语义 NotFound，防枚举。
        NotFoundException ex = assertThrows(NotFoundException.class, () ->
                service.listProjects("org_a", "prn_user", false));
        assertEquals(ErrorCode.ORGANIZATION_NOT_FOUND, ex.errorCode());
    }

    @Test
    void listProjectsReturnsForMember() {
        given(organizationRepository.existsByOrganizationId("org_a")).willReturn(true);
        given(memberRepository.exists("org_a", "prn_user")).willReturn(true);
        given(projectRepository.findByOrganizationId("org_a")).willReturn(List.of());
        assertEquals(0, service.listProjects("org_a", "prn_user", false).size());
    }

    @Test
    void listProjectsReturnsForPlatformAdminWithoutMembership() {
        given(organizationRepository.existsByOrganizationId("org_a")).willReturn(true);
        given(projectRepository.findByOrganizationId("org_a")).willReturn(List.of());
        // 平台管理员无需成员关系即可访问任意组织。
        assertEquals(0, service.listProjects("org_a", "prn_admin", true).size());
    }

    @Test
    void createProjectConflictsOnDuplicateCode() {
        given(organizationRepository.existsByOrganizationId("org_a")).willReturn(true);
        given(memberRepository.exists("org_a", "prn_user")).willReturn(true);
        given(projectRepository.existsByOrganizationIdAndCode("org_a", "alpha")).willReturn(true);
        ConflictException ex = assertThrows(ConflictException.class, () ->
                service.createProject(new CreateProjectCommand("org_a", "alpha", "Alpha"),
                        "prn_user", false, "prn_user"));
        assertEquals(ErrorCode.PROJECT_ALREADY_EXISTS, ex.errorCode());
    }

    @Test
    void createProjectPersistsAndAuditsForMember() {
        given(organizationRepository.existsByOrganizationId("org_a")).willReturn(true);
        given(memberRepository.exists("org_a", "prn_user")).willReturn(true);
        given(projectRepository.existsByOrganizationIdAndCode("org_a", "alpha")).willReturn(false);
        ProjectView view = service.createProject(new CreateProjectCommand("org_a", "alpha", "Alpha"),
                "prn_user", false, "prn_user");
        assertEquals("prj_01", view.projectId());
        assertEquals("org_a", view.organizationId());
        verify(projectRepository).insert(any(Project.class));
        verify(auditPort).record(eq("PROJECT_CREATED"), eq("prn_user"), eq("prj_01"), any());
    }
}
