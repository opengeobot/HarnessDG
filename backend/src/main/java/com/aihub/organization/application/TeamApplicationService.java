/*
 * 功能: Team 应用服务，编排 Team CRUD 与成员管理用例。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
package com.aihub.organization.application;

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
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Team 应用服务。
 *
 * <p>编排 Team 创建、更新、查询以及成员添加/移除用例。
 * 防资源枚举：Team 不存在对外统一返回 {@code TEAM_NOT_FOUND}。
 */
@Service
public class TeamApplicationService {

    private final TeamRepository teamRepository;
    private final PrincipalQueryApplicationService principalQuery;
    private final IdGenerator idGenerator;
    private final AuditPort auditPort;
    private final Clock clock;

    public TeamApplicationService(TeamRepository teamRepository,
                                  PrincipalQueryApplicationService principalQuery,
                                  IdGenerator idGenerator,
                                  AuditPort auditPort,
                                  Clock clock) {
        this.teamRepository = teamRepository;
        this.principalQuery = principalQuery;
        this.idGenerator = idGenerator;
        this.auditPort = auditPort;
        this.clock = clock;
    }

    /**
     * 列出组织下的所有 Team。
     */
    @Transactional(readOnly = true)
    public List<TeamView> listTeams(String organizationId) {
        return teamRepository.findByOrganizationId(organizationId).stream()
                .map(TeamView::from).toList();
    }

    /**
     * 查询 Team 详情。
     */
    @Transactional(readOnly = true)
    public TeamView getTeam(String teamId) {
        Team team = requireTeamExists(teamId);
        return TeamView.from(team);
    }

    /**
     * 创建 Team。
     */
    @Transactional
    public TeamView createTeam(CreateTeamCommand command, String actorId) {
        validateName(command.name());
        if (teamRepository.existsByName(command.organizationId(), command.name())) {
            throw new ConflictException(ErrorCode.TEAM_ALREADY_EXISTS,
                    "team name already exists in organization",
                    Map.of("organizationId", command.organizationId(), "name", command.name()));
        }
        String teamId = idGenerator.generate(IdPrefix.TEAM);
        Team team = Team.create(teamId, command.organizationId(), command.name(),
                command.description(), actorId);
        teamRepository.insert(team);
        auditPort.record("TEAM_CREATED", actorId, teamId,
                Map.of("organizationId", command.organizationId(), "name", command.name()));
        return TeamView.from(team);
    }

    /**
     * 更新 Team。
     */
    @Transactional
    public TeamView updateTeam(String teamId, UpdateTeamCommand command, String actorId) {
        Team team = requireTeamExists(teamId);
        validateName(command.name());
        Team updated = new Team(team.teamId(), team.organizationId(), command.name(),
                command.description(), command.status(), team.createdBy(),
                team.createdAt(), clock.instant(), team.rowVersion());
        teamRepository.update(updated);
        auditPort.record("TEAM_UPDATED", actorId, teamId,
                Map.of("name", command.name(), "status", command.status()));
        return TeamView.from(new Team(updated.teamId(), updated.organizationId(), updated.name(),
                updated.description(), updated.status(), updated.createdBy(),
                updated.createdAt(), updated.updatedAt(), updated.rowVersion() + 1));
    }

    /**
     * 列出 Team 成员。
     */
    @Transactional(readOnly = true)
    public List<TeamMemberView> listMembers(String teamId) {
        requireTeamExists(teamId);
        return teamRepository.findMembersByTeamId(teamId).stream()
                .map(TeamMemberView::from).toList();
    }

    /**
     * 添加 Team 成员。
     */
    @Transactional
    public TeamMemberView addMember(AddTeamMemberCommand command, String actorId) {
        String teamId = command.teamId();
        String principalId = command.principalId();
        requireTeamExists(teamId);
        if (!principalQuery.existsByPrincipalId(principalId)) {
            throw new NotFoundException(ErrorCode.PRINCIPAL_NOT_FOUND,
                    "principal not found", Map.of("principalId", principalId));
        }
        if (teamRepository.findMember(teamId, principalId).isPresent()) {
            throw new ConflictException(ErrorCode.TEAM_MEMBER_ALREADY_EXISTS,
                    "team member already exists",
                    Map.of("teamId", teamId, "principalId", principalId));
        }
        String role = command.role() != null ? command.role() : "MEMBER";
        TeamMember member = new TeamMember(teamId, principalId, role, actorId, clock.instant());
        teamRepository.addMember(member);
        auditPort.record("TEAM_MEMBER_ADDED", actorId, teamId,
                Map.of("principalId", principalId, "role", role));
        return TeamMemberView.from(member);
    }

    /**
     * 移除 Team 成员。
     */
    @Transactional
    public void removeMember(String teamId, String principalId, String actorId) {
        requireTeamExists(teamId);
        if (teamRepository.findMember(teamId, principalId).isEmpty()) {
            throw new NotFoundException(ErrorCode.TEAM_MEMBER_NOT_FOUND,
                    "team member not found",
                    Map.of("teamId", teamId, "principalId", principalId));
        }
        teamRepository.removeMember(teamId, principalId);
        auditPort.record("TEAM_MEMBER_REMOVED", actorId, teamId,
                Map.of("principalId", principalId));
    }

    private Team requireTeamExists(String teamId) {
        return teamRepository.findByTeamId(teamId).orElseThrow(() ->
                new NotFoundException(ErrorCode.TEAM_NOT_FOUND,
                        "team not found", Map.of("teamId", teamId)));
    }

    static void validateName(String name) {
        if (name == null || name.isBlank() || name.length() > 128) {
            throw new ValidationException("name is required and must be at most 128 characters");
        }
    }
}
