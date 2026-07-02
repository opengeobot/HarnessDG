/*
 * 功能: 项目仓储端口，约定项目聚合的持久化与按组织查询。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.organization.domain;

import java.util.List;
import java.util.Optional;

/**
 * 项目仓储端口。
 *
 * <p>领域层只依赖本端口；实现位于 infrastructure。
 */
public interface ProjectRepository {

    /**
     * @param projectId 业务项目 ID
     * @return 项目聚合（不存在返回空）
     */
    Optional<Project> findByProjectId(String projectId);

    /**
     * 列出指定组织下的全部项目。
     *
     * @param organizationId 组织 ID
     */
    List<Project> findByOrganizationId(String organizationId);

    /**
     * @param organizationId 组织 ID
     * @param code           项目编码
     * @return (组织, 编码) 是否已被占用
     */
    boolean existsByOrganizationIdAndCode(String organizationId, String code);

    /**
     * 持久化新项目。
     */
    void insert(Project project);
}
