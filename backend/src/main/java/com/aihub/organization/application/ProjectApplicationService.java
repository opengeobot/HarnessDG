/*
 * 功能: 项目应用服务，编排组织项目的查询与创建用例（含组织成员可达性校验/防枚举）。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.organization.application;

import static com.aihub.organization.application.OrganizationApplicationService.validateCode;
import static com.aihub.organization.application.OrganizationApplicationService.validateName;

import com.aihub.organization.application.OrganizationDtos.CreateProjectCommand;
import com.aihub.organization.application.OrganizationDtos.ProjectView;
import com.aihub.organization.domain.OrganizationMemberRepository;
import com.aihub.organization.domain.OrganizationRepository;
import com.aihub.organization.domain.OrganizationStatus;
import com.aihub.organization.domain.Project;
import com.aihub.organization.domain.ProjectRepository;
import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目应用服务。
 *
 * <p>编排组织项目用例：列出组织项目、创建项目。组织可达性由应用层校验——平台组织管理员可见任意组织，
 * 否则仅当主体为该组织成员时可见；组织不存在与非成员访问对外统一返回 {@code ORGANIZATION_NOT_FOUND}（防枚举）。
 * 平台管理员判定由适配层经 AuthorizationService 完成后以 {@code platformAdmin} 入参传入，应用层不依赖 authorization。
 */
@Service
public class ProjectApplicationService {

    private final ProjectRepository projectRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final IdGenerator idGenerator;
    private final AuditPort auditPort;
    private final Clock clock;

    public ProjectApplicationService(ProjectRepository projectRepository,
                                     OrganizationRepository organizationRepository,
                                     OrganizationMemberRepository memberRepository,
                                     IdGenerator idGenerator,
                                     AuditPort auditPort,
                                     Clock clock) {
        this.projectRepository = projectRepository;
        this.organizationRepository = organizationRepository;
        this.memberRepository = memberRepository;
        this.idGenerator = idGenerator;
        this.auditPort = auditPort;
        this.clock = clock;
    }

    /**
     * 列出组织项目（成员隔离）。
     *
     * @param organizationId 组织 ID
     * @param principalId    当前主体 ID
     * @param platformAdmin  是否平台组织管理员
     * @return 项目视图列表
     */
    @Transactional(readOnly = true)
    public List<ProjectView> listProjects(String organizationId, String principalId, boolean platformAdmin) {
        requireOrganizationAccessible(organizationId, principalId, platformAdmin);
        return projectRepository.findByOrganizationId(organizationId).stream()
                .map(ProjectView::from).toList();
    }

    /**
     * 创建组织项目。
     */
    @Transactional
    public ProjectView createProject(CreateProjectCommand command, String principalId,
                                     boolean platformAdmin, String actorId) {
        String organizationId = command.organizationId();
        requireOrganizationAccessible(organizationId, principalId, platformAdmin);
        validateCode(command.code());
        validateName(command.name());
        if (projectRepository.existsByOrganizationIdAndCode(organizationId, command.code())) {
            throw new ConflictException(ErrorCode.PROJECT_ALREADY_EXISTS,
                    "project code already exists in organization",
                    Map.of("organizationId", organizationId, "code", command.code()));
        }
        Project project = new Project(
                idGenerator.generate(IdPrefix.PROJECT),
                organizationId,
                command.code().trim(),
                command.name().trim(),
                null,
                OrganizationStatus.ACTIVE,
                actorId,
                clock.instant(),
                clock.instant(),
                0);
        projectRepository.insert(project);
        auditPort.record("PROJECT_CREATED", actorId, project.projectId(),
                Map.of("organizationId", organizationId, "code", project.code(), "name", project.name()));
        return ProjectView.from(project);
    }

    /**
     * 组织可达性校验（防枚举）：组织不存在或主体非成员均返回 {@code ORGANIZATION_NOT_FOUND}。
     */
    private void requireOrganizationAccessible(String organizationId, String principalId,
                                               boolean platformAdmin) {
        if (!organizationRepository.existsByOrganizationId(organizationId)) {
            throw new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND,
                    "organization not found", Map.of("organizationId", organizationId));
        }
        if (platformAdmin) {
            return;
        }
        if (!memberRepository.exists(organizationId, principalId)) {
            // 与"组织不存在"同语义，避免通过 403/404 差异枚举私有组织资源。
            throw new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND,
                    "organization not found", Map.of("organizationId", organizationId));
        }
    }
}
