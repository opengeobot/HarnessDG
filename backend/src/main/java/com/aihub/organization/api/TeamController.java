/*
 * 功能: Team 管理 REST 适配器，提供 Team CRUD 与成员管理接口。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
package com.aihub.organization.api;

import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.organization.api.TeamRequests.AddTeamMemberRequest;
import com.aihub.organization.api.TeamRequests.CreateTeamRequest;
import com.aihub.organization.api.TeamRequests.UpdateTeamRequest;
import com.aihub.organization.application.TeamApplicationService;
import com.aihub.organization.application.TeamDtos.AddTeamMemberCommand;
import com.aihub.organization.application.TeamDtos.CreateTeamCommand;
import com.aihub.organization.application.TeamDtos.TeamMemberView;
import com.aihub.organization.application.TeamDtos.TeamView;
import com.aihub.organization.application.TeamDtos.UpdateTeamCommand;
import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.error.ValidationException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Team 管理 REST 适配器。
 *
 * <p>授权判定走统一 {@link AuthorizationService}：读操作需 {@code project:view}，
 * 写操作需 {@code team:manage}。
 */
@RestController
@RequestMapping("/api/v1/system/organizations/{organizationId}/teams")
public class TeamController {

    private final TeamApplicationService teamService;
    private final AuthorizationService authorizationService;

    public TeamController(TeamApplicationService teamService,
                          AuthorizationService authorizationService) {
        this.teamService = teamService;
        this.authorizationService = authorizationService;
    }

    /**
     * 列出组织下的 Team。
     */
    @GetMapping
    public ApiResponse<List<TeamView>> listTeams(@PathVariable String organizationId) {
        authorizationService.requirePermission(Permissions.PROJECT_VIEW);
        return OrganizationApiContext.respond(teamService.listTeams(organizationId));
    }

    /**
     * 创建 Team。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TeamView> createTeam(@PathVariable String organizationId,
                                            @RequestBody CreateTeamRequest request) {
        authorizationService.requirePermission(Permissions.TEAM_MANAGE);
        if (request == null) {
            throw new ValidationException("request body is required");
        }
        TeamView view = teamService.createTeam(
                new CreateTeamCommand(organizationId, request.name(), request.description()),
                OrganizationApiContext.principalId());
        return OrganizationApiContext.respond(view);
    }

    /**
     * Team 详情。
     */
    @GetMapping("/{teamId}")
    public ApiResponse<TeamView> getTeam(@PathVariable String organizationId,
                                         @PathVariable String teamId) {
        authorizationService.requirePermission(Permissions.PROJECT_VIEW);
        return OrganizationApiContext.respond(teamService.getTeam(teamId));
    }

    /**
     * 更新 Team。
     */
    @PutMapping("/{teamId}")
    public ApiResponse<TeamView> updateTeam(@PathVariable String organizationId,
                                            @PathVariable String teamId,
                                            @RequestBody UpdateTeamRequest request) {
        authorizationService.requirePermission(Permissions.TEAM_MANAGE);
        if (request == null) {
            throw new ValidationException("request body is required");
        }
        TeamView view = teamService.updateTeam(teamId,
                new UpdateTeamCommand(request.name(), request.description(), request.status()),
                OrganizationApiContext.principalId());
        return OrganizationApiContext.respond(view);
    }

    /**
     * 列出 Team 成员。
     */
    @GetMapping("/{teamId}/members")
    public ApiResponse<List<TeamMemberView>> listMembers(@PathVariable String organizationId,
                                                         @PathVariable String teamId) {
        authorizationService.requirePermission(Permissions.PROJECT_VIEW);
        return OrganizationApiContext.respond(teamService.listMembers(teamId));
    }

    /**
     * 添加 Team 成员。
     */
    @PostMapping("/{teamId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TeamMemberView> addMember(@PathVariable String organizationId,
                                                 @PathVariable String teamId,
                                                 @RequestBody AddTeamMemberRequest request) {
        authorizationService.requirePermission(Permissions.TEAM_MANAGE);
        if (request == null) {
            throw new ValidationException("request body is required");
        }
        TeamMemberView view = teamService.addMember(
                new AddTeamMemberCommand(teamId, request.principalId(), request.role()),
                OrganizationApiContext.principalId());
        return OrganizationApiContext.respond(view);
    }

    /**
     * 移除 Team 成员。
     */
    @DeleteMapping("/{teamId}/members/{principalId}")
    public ApiResponse<Void> removeMember(@PathVariable String organizationId,
                                          @PathVariable String teamId,
                                          @PathVariable String principalId) {
        authorizationService.requirePermission(Permissions.TEAM_MANAGE);
        teamService.removeMember(teamId, principalId, OrganizationApiContext.principalId());
        return OrganizationApiContext.respond(null);
    }
}
