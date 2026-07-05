/*
 * 功能: TeamApplicationService 单元测试——Team CRUD 与成员管理用例规则。
 * 时间: 2026-07-05
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

import com.aihub.identity.application.PrincipalQueryApplicationService;
import com.aihub.organization.application.TeamDtos.AddTeamMemberCommand;
import com.aihub.organization.application.TeamDtos.CreateTeamCommand;
import com.aihub.organization.application.TeamDtos.TeamMemberView;
import com.aihub.organization.application.TeamDtos.TeamView;
import com.aihub.organization.application.TeamDtos.UpdateTeamCommand;
import com.aihub.organization.domain.Team;
import com.aihub.organization.domain.TeamMember;
import com.aihub.organization.domain.TeamRepository;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * TeamApplicationService 单元测试。
 *
 * <p>覆盖 Team 创建（名称冲突、参数校验）、更新、查询、成员添加（重复/主体不存在/Team 不存在）、
 * 成员移除（不存在 NotFound）。
 */
class TeamApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-05T00:00:00Z");

    private TeamRepository teamRepository;
    private PrincipalQueryApplicationService principalQuery;
    private IdGenerator idGenerator;
    private AuditPort auditPort;
    private TeamApplicationService service;

    @BeforeEach
    void setUp() {
        teamRepository = Mockito.mock(TeamRepository.class);
        principalQuery = Mockito.mock(PrincipalQueryApplicationService.class);
        idGenerator = Mockito.mock(IdGenerator.class);
        auditPort = Mockito.mock(AuditPort.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new TeamApplicationService(teamRepository, principalQuery,
                idGenerator, auditPort, clock);
        lenient().when(idGenerator.generate(IdPrefix.TEAM)).thenReturn("team_01");
    }

    private Team team(String teamId, String orgId, String name) {
        return new Team(teamId, orgId, name, "desc", "ACTIVE",
                "prn_admin", NOW, NOW, 1);
    }

    @Test
    void listTeamsReturnsTeamsForOrganization() {
        given(teamRepository.findByOrganizationId("org_01"))
                .willReturn(List.of(team("team_01", "org_01", "Alpha")));
        List<TeamView> views = service.listTeams("org_01");
        assertEquals(1, views.size());
        assertEquals("team_01", views.get(0).teamId());
    }

    @Test
    void getTeamReturnsTeamWhenExists() {
        given(teamRepository.findByTeamId("team_01"))
                .willReturn(Optional.of(team("team_01", "org_01", "Alpha")));
        TeamView view = service.getTeam("team_01");
        assertEquals("Alpha", view.name());
    }

    @Test
    void getTeamThrowsNotFoundWhenMissing() {
        given(teamRepository.findByTeamId("team_ghost"))
                .willReturn(Optional.empty());
        NotFoundException ex = assertThrows(NotFoundException.class, () ->
                service.getTeam("team_ghost"));
        assertEquals(ErrorCode.TEAM_NOT_FOUND, ex.errorCode());
    }

    @Test
    void createTeamRejectsInvalidName() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                service.createTeam(new CreateTeamCommand("org_01", "", null), "prn_admin"));
        assertEquals(ErrorCode.COMMON_INVALID_ARGUMENT, ex.errorCode());
    }

    @Test
    void createTeamConflictsOnDuplicateName() {
        given(teamRepository.existsByName("org_01", "Alpha")).willReturn(true);
        ConflictException ex = assertThrows(ConflictException.class, () ->
                service.createTeam(new CreateTeamCommand("org_01", "Alpha", null), "prn_admin"));
        assertEquals(ErrorCode.TEAM_ALREADY_EXISTS, ex.errorCode());
    }

    @Test
    void createTeamPersistsAndAudits() {
        given(teamRepository.existsByName("org_01", "Alpha")).willReturn(false);
        TeamView view = service.createTeam(
                new CreateTeamCommand("org_01", "Alpha", "description"), "prn_admin");
        assertEquals("team_01", view.teamId());
        assertEquals("Alpha", view.name());
        verify(teamRepository).insert(any(Team.class));
        verify(auditPort).record(eq("TEAM_CREATED"), eq("prn_admin"), eq("team_01"), any());
    }

    @Test
    void updateTeamThrowsNotFoundWhenMissing() {
        given(teamRepository.findByTeamId("team_ghost"))
                .willReturn(Optional.empty());
        NotFoundException ex = assertThrows(NotFoundException.class, () ->
                service.updateTeam("team_ghost",
                        new UpdateTeamCommand("Beta", "new desc", "ACTIVE"), "prn_admin"));
        assertEquals(ErrorCode.TEAM_NOT_FOUND, ex.errorCode());
    }

    @Test
    void updateTeamPersistsAndAudits() {
        given(teamRepository.findByTeamId("team_01"))
                .willReturn(Optional.of(team("team_01", "org_01", "Alpha")));
        TeamView view = service.updateTeam("team_01",
                new UpdateTeamCommand("Beta", "new desc", "ACTIVE"), "prn_admin");
        assertEquals("Beta", view.name());
        verify(teamRepository).update(any(Team.class));
        verify(auditPort).record(eq("TEAM_UPDATED"), eq("prn_admin"), eq("team_01"), any());
    }

    @Test
    void addMemberConflictsWhenAlreadyMember() {
        given(teamRepository.findByTeamId("team_01"))
                .willReturn(Optional.of(team("team_01", "org_01", "Alpha")));
        given(principalQuery.existsByPrincipalId("prn_user")).willReturn(true);
        given(teamRepository.findMember("team_01", "prn_user"))
                .willReturn(Optional.of(new TeamMember("team_01", "prn_user", "MEMBER", "prn_admin", NOW)));
        ConflictException ex = assertThrows(ConflictException.class, () ->
                service.addMember(new AddTeamMemberCommand("team_01", "prn_user", "MEMBER"), "prn_admin"));
        assertEquals(ErrorCode.TEAM_MEMBER_ALREADY_EXISTS, ex.errorCode());
    }

    @Test
    void addMemberFailsWhenPrincipalNotFound() {
        given(teamRepository.findByTeamId("team_01"))
                .willReturn(Optional.of(team("team_01", "org_01", "Alpha")));
        given(principalQuery.existsByPrincipalId("prn_ghost")).willReturn(false);
        NotFoundException ex = assertThrows(NotFoundException.class, () ->
                service.addMember(new AddTeamMemberCommand("team_01", "prn_ghost", "MEMBER"), "prn_admin"));
        assertEquals(ErrorCode.PRINCIPAL_NOT_FOUND, ex.errorCode());
    }

    @Test
    void addMemberFailsWhenTeamMissing() {
        given(teamRepository.findByTeamId("team_ghost"))
                .willReturn(Optional.empty());
        NotFoundException ex = assertThrows(NotFoundException.class, () ->
                service.addMember(new AddTeamMemberCommand("team_ghost", "prn_user", "MEMBER"), "prn_admin"));
        assertEquals(ErrorCode.TEAM_NOT_FOUND, ex.errorCode());
    }

    @Test
    void addMemberSucceedsAndAudits() {
        given(teamRepository.findByTeamId("team_01"))
                .willReturn(Optional.of(team("team_01", "org_01", "Alpha")));
        given(principalQuery.existsByPrincipalId("prn_user")).willReturn(true);
        given(teamRepository.findMember("team_01", "prn_user"))
                .willReturn(Optional.empty());
        TeamMemberView view = service.addMember(
                new AddTeamMemberCommand("team_01", "prn_user", "MEMBER"), "prn_admin");
        assertEquals("prn_user", view.principalId());
        verify(teamRepository).addMember(any(TeamMember.class));
        verify(auditPort).record(eq("TEAM_MEMBER_ADDED"), eq("prn_admin"), eq("team_01"), any());
    }

    @Test
    void removeMemberNotFoundWhenNotMember() {
        given(teamRepository.findByTeamId("team_01"))
                .willReturn(Optional.of(team("team_01", "org_01", "Alpha")));
        given(teamRepository.findMember("team_01", "prn_user"))
                .willReturn(Optional.empty());
        NotFoundException ex = assertThrows(NotFoundException.class, () ->
                service.removeMember("team_01", "prn_user", "prn_admin"));
        assertEquals(ErrorCode.TEAM_MEMBER_NOT_FOUND, ex.errorCode());
    }

    @Test
    void removeMemberSucceedsAndAudits() {
        given(teamRepository.findByTeamId("team_01"))
                .willReturn(Optional.of(team("team_01", "org_01", "Alpha")));
        given(teamRepository.findMember("team_01", "prn_user"))
                .willReturn(Optional.of(new TeamMember("team_01", "prn_user", "MEMBER", "prn_admin", NOW)));
        service.removeMember("team_01", "prn_user", "prn_admin");
        verify(teamRepository).removeMember("team_01", "prn_user");
        verify(auditPort).record(eq("TEAM_MEMBER_REMOVED"), eq("prn_admin"), eq("team_01"), any());
    }
}
