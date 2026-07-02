/*
 * 功能: OrganizationApplicationService 单元测试——组织/成员用例规则与成员隔离列表查询。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.organization.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import com.aihub.identity.application.PrincipalQueryApplicationService;
import com.aihub.organization.application.OrganizationDtos.AddMemberCommand;
import com.aihub.organization.application.OrganizationDtos.CreateOrganizationCommand;
import com.aihub.organization.application.OrganizationDtos.OrganizationView;
import com.aihub.organization.domain.MemberRole;
import com.aihub.organization.domain.Organization;
import com.aihub.organization.domain.OrganizationMemberRepository;
import com.aihub.organization.domain.OrganizationRepository;
import com.aihub.organization.domain.OrganizationStatus;
import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * OrganizationApplicationService 单元测试。
 *
 * <p>覆盖成员关系领域规则（重复添加冲突、移除不存在 NotFound、组织不存在 NotFound）、
 * 组织创建（编码冲突、参数校验）与成员隔离列表查询（平台管理员见全部、普通成员仅见所属组织）。
 */
class OrganizationApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-30T00:00:00Z");

    private OrganizationRepository organizationRepository;
    private OrganizationMemberRepository memberRepository;
    private PrincipalQueryApplicationService principalQuery;
    private IdGenerator idGenerator;
    private AuditPort auditPort;
    private OrganizationApplicationService service;

    @BeforeEach
    void setUp() {
        organizationRepository = Mockito.mock(OrganizationRepository.class);
        memberRepository = Mockito.mock(OrganizationMemberRepository.class);
        principalQuery = Mockito.mock(PrincipalQueryApplicationService.class);
        idGenerator = Mockito.mock(IdGenerator.class);
        auditPort = Mockito.mock(AuditPort.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new OrganizationApplicationService(organizationRepository, memberRepository,
                principalQuery, idGenerator, auditPort, clock);
        lenient().when(idGenerator.generate(IdPrefix.ORGANIZATION)).thenReturn("org_01");
    }

    private Organization org(String id, String code) {
        return new Organization(id, code, code, null, null, OrganizationStatus.ACTIVE,
                "prn_admin", NOW, NOW, 0);
    }

    @Test
    void listOrganizationsReturnsAllWhenPlatformAdmin() {
        given(organizationRepository.findAll()).willReturn(List.of(org("org_a", "alpha"), org("org_b", "beta")));
        List<OrganizationView> views = service.listOrganizations("prn_admin", true);
        assertEquals(2, views.size());
        assertEquals("alpha", views.get(0).code());
    }

    @Test
    void listOrganizationsReturnsOnlyMembershipOrgsForNonAdmin() {
        given(organizationRepository.findByPrincipalMembership("prn_user"))
                .willReturn(List.of(org("org_a", "alpha")));
        List<OrganizationView> views = service.listOrganizations("prn_user", false);
        assertEquals(1, views.size());
        assertEquals("org_a", views.get(0).organizationId());
        // 确认走成员隔离下推查询，而非全量。
        verify(organizationRepository).findByPrincipalMembership("prn_user");
    }

    @Test
    void createOrganizationRejectsInvalidCode() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                service.createOrganization(new CreateOrganizationCommand("BAD CODE", "name", null), "prn_admin"));
        assertEquals(ErrorCode.COMMON_INVALID_ARGUMENT, ex.errorCode());
    }

    @Test
    void createOrganizationConflictsOnDuplicateCode() {
        given(organizationRepository.existsByCode("alpha")).willReturn(true);
        ConflictException ex = assertThrows(ConflictException.class, () ->
                service.createOrganization(new CreateOrganizationCommand("alpha", "Alpha", null), "prn_admin"));
        assertEquals(ErrorCode.ORGANIZATION_ALREADY_EXISTS, ex.errorCode());
    }

    @Test
    void createOrganizationPersistsAndAudits() {
        given(organizationRepository.existsByCode("alpha")).willReturn(false);
        OrganizationView view = service.createOrganization(
                new CreateOrganizationCommand("alpha", "Alpha", "gitea-alpha"), "prn_admin");
        assertEquals("org_01", view.organizationId());
        assertEquals("alpha", view.code());
        assertEquals("gitea-alpha", view.giteaOrganization());
        verify(organizationRepository).insert(any(Organization.class));
        verify(auditPort).record(eq("ORGANIZATION_CREATED"), eq("prn_admin"), eq("org_01"), any());
    }

    @Test
    void addMemberConflictsWhenAlreadyMember() {
        given(organizationRepository.existsByOrganizationId("org_a")).willReturn(true);
        given(principalQuery.existsByPrincipalId("prn_user")).willReturn(true);
        given(memberRepository.exists("org_a", "prn_user")).willReturn(true);
        ConflictException ex = assertThrows(ConflictException.class, () ->
                service.addMember(new AddMemberCommand("org_a", "prn_user"), MemberRole.MEMBER, "prn_admin"));
        assertEquals(ErrorCode.ORGANIZATION_MEMBER_ALREADY_EXISTS, ex.errorCode());
    }

    @Test
    void addMemberFailsWhenPrincipalNotFound() {
        given(organizationRepository.existsByOrganizationId("org_a")).willReturn(true);
        given(principalQuery.existsByPrincipalId("prn_ghost")).willReturn(false);
        NotFoundException ex = assertThrows(NotFoundException.class, () ->
                service.addMember(new AddMemberCommand("org_a", "prn_ghost"), MemberRole.MEMBER, "prn_admin"));
        assertEquals(ErrorCode.PRINCIPAL_NOT_FOUND, ex.errorCode());
    }

    @Test
    void addMemberFailsWhenOrganizationMissing() {
        given(organizationRepository.existsByOrganizationId("org_x")).willReturn(false);
        NotFoundException ex = assertThrows(NotFoundException.class, () ->
                service.addMember(new AddMemberCommand("org_x", "prn_user"), MemberRole.MEMBER, "prn_admin"));
        assertEquals(ErrorCode.ORGANIZATION_NOT_FOUND, ex.errorCode());
    }

    @Test
    void removeMemberNotFoundWhenNotMember() {
        given(organizationRepository.existsByOrganizationId("org_a")).willReturn(true);
        given(memberRepository.remove("org_a", "prn_user")).willReturn(false);
        NotFoundException ex = assertThrows(NotFoundException.class, () ->
                service.removeMember("org_a", "prn_user", "prn_admin"));
        assertEquals(ErrorCode.ORGANIZATION_MEMBER_NOT_FOUND, ex.errorCode());
    }

    @Test
    void removeMemberSucceedsAndAudits() {
        given(organizationRepository.existsByOrganizationId("org_a")).willReturn(true);
        given(memberRepository.remove("org_a", "prn_user")).willReturn(true);
        service.removeMember("org_a", "prn_user", "prn_admin");
        verify(auditPort).record(eq("ORGANIZATION_MEMBER_REMOVED"), eq("prn_admin"), eq("org_a"), any());
    }

    @Test
    void membershipQueryReturnsPrincipalOrganizationScope() {
        // 成员隔离下推查询：repository 返回主体所属组织 ID 集合（供 AccessScope 接通）。
        given(memberRepository.findOrganizationIdsByPrincipal("prn_user"))
                .willReturn(Set.of("org_a", "org_b"));
        Set<String> scope = memberRepository.findOrganizationIdsByPrincipal("prn_user");
        assertEquals(Set.of("org_a", "org_b"), scope);
        assertTrue(scope.contains("org_a"));
    }
}
