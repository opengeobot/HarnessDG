/*
 * 功能: organization 应用层视图与命令对象集合，对外只暴露视图，绝不返回持久化实体。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.organization.application;

import com.aihub.organization.domain.Organization;
import com.aihub.organization.domain.OrganizationMember;
import com.aihub.organization.domain.OrganizationStatus;
import com.aihub.organization.domain.Project;
import java.time.Instant;

/**
 * organization 应用层 DTO 集合。
 *
 * <p>承载用例输入命令与对外视图；字段名与 OpenAPI 契约 schema 对齐。视图由领域对象转换得到，
 * 不外泄持久化实体。
 */
public final class OrganizationDtos {

    private OrganizationDtos() {
    }

    /** 组织视图（OrganizationView）。 */
    public record OrganizationView(String organizationId, String code, String name,
                                   String giteaOrganization, OrganizationStatus status,
                                   Instant createdAt) {
        public static OrganizationView from(Organization org) {
            return new OrganizationView(org.organizationId(), org.code(), org.name(),
                    org.giteaOrganization(), org.status(), org.createdAt());
        }
    }

    /** 组织成员视图（OrganizationMemberView）。 */
    public record OrganizationMemberView(String organizationId, String principalId, Instant joinedAt) {
        public static OrganizationMemberView from(OrganizationMember member) {
            return new OrganizationMemberView(member.organizationId(), member.principalId(),
                    member.createdAt());
        }
    }

    /** 项目视图（ProjectView）。 */
    public record ProjectView(String projectId, String organizationId, String code, String name,
                              OrganizationStatus status, Instant createdAt) {
        public static ProjectView from(Project project) {
            return new ProjectView(project.projectId(), project.organizationId(), project.code(),
                    project.name(), project.status(), project.createdAt());
        }
    }

    /** 创建组织命令（CreateOrganizationRequest）。 */
    public record CreateOrganizationCommand(String code, String name, String giteaOrganization) {
    }

    /** 创建项目命令（CreateProjectRequest）。 */
    public record CreateProjectCommand(String organizationId, String code, String name) {
    }

    /** 添加组织成员命令（AddOrganizationMemberRequest）。 */
    public record AddMemberCommand(String organizationId, String principalId) {
    }
}
