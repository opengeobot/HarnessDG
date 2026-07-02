/*
 * 功能: 组织项目管理 REST 适配器，提供组织项目列表与创建接口。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.organization.api;

import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.organization.api.OrganizationRequests.CreateProjectRequest;
import com.aihub.organization.application.OrganizationDtos.CreateProjectCommand;
import com.aihub.organization.application.OrganizationDtos.ProjectView;
import com.aihub.organization.application.ProjectApplicationService;
import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.error.ValidationException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 组织项目管理 REST 适配器。
 *
 * <p>授权判定走统一 {@link AuthorizationService}（fail-closed）：列表需 {@code project:view}，
 * 创建需 {@code project:manage}。组织可达性（成员隔离）由应用层校验，非成员访问返回防枚举 NotFound。
 */
@RestController
@RequestMapping("/api/v1/system/organizations")
public class ProjectController {

    private final ProjectApplicationService projectService;
    private final AuthorizationService authorizationService;

    public ProjectController(ProjectApplicationService projectService,
                             AuthorizationService authorizationService) {
        this.projectService = projectService;
        this.authorizationService = authorizationService;
    }

    /**
     * 查询组织项目（仅成员或平台组织管理员可见，否则防枚举 NotFound）。
     */
    @GetMapping("/{organizationId}/projects")
    public ApiResponse<List<ProjectView>> listProjects(@PathVariable String organizationId) {
        authorizationService.requirePermission(Permissions.PROJECT_VIEW);
        boolean platformAdmin = authorizationService.isPermitted(Permissions.ORGANIZATION_MANAGE);
        return OrganizationApiContext.respond(
                projectService.listProjects(organizationId, OrganizationApiContext.principalId(), platformAdmin));
    }

    /**
     * 创建组织项目。
     */
    @PostMapping("/{organizationId}/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProjectView> createProject(@PathVariable String organizationId,
                                                  @RequestBody CreateProjectRequest request) {
        authorizationService.requirePermission(Permissions.PROJECT_MANAGE);
        if (request == null) {
            throw new ValidationException("request body is required");
        }
        boolean platformAdmin = authorizationService.isPermitted(Permissions.ORGANIZATION_MANAGE);
        ProjectView view = projectService.createProject(
                new CreateProjectCommand(organizationId, request.code(), request.name()),
                OrganizationApiContext.principalId(), platformAdmin, OrganizationApiContext.principalId());
        return OrganizationApiContext.respond(view);
    }
}
