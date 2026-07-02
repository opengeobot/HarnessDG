/*
 * 功能: 访问作用域值对象，承载主体的数据库阶段权限下推所需信息（供资产等列表查询构造 SQL 谓词）。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.authorization.domain;

import java.util.List;
import java.util.Set;

/**
 * 访问作用域（权限下推值对象）。
 *
 * <p>由 {@code AuthorizationService} 依据当前主体的 RBAC 角色绑定与资源 ACL 解析得到，供资产等业务模块
 * 在 SQL {@code WHERE} 阶段过滤可见数据，杜绝"先全量查询再 Java 过滤"。
 *
 * <p>本对象只描述"可见性边界"，不耦合任何具体表结构：业务模块据此自行拼接谓词，例如
 * 平台管理员可见全部；否则可见自身为 Owner、所属组织/项目，或在 {@code accessibleResourceIds} 名单内的资源。
 *
 * @param principalId           主体 ID（可空表示匿名）
 * @param platformAdmin         是否平台管理员（拥有 {@code authorization:manage} 等全量管理权限的等价标记）
 * @param organizationIds       可访问组织 ID 集合
 * @param projectIds            可访问项目 ID 集合
 * @param accessibleResourceIds 通过资源 ACL 显式可访问的指定动作资源 ID 集合
 */
public record AccessScope(String principalId,
                          boolean platformAdmin,
                          Set<String> organizationIds,
                          Set<String> projectIds,
                          Set<String> accessibleResourceIds) {

    /**
     * 紧凑构造器：集合做不可变拷贝，避免外部修改污染下推条件。
     */
    public AccessScope {
        organizationIds = organizationIds == null ? Set.of() : Set.copyOf(organizationIds);
        projectIds = projectIds == null ? Set.of() : Set.copyOf(projectIds);
        accessibleResourceIds = accessibleResourceIds == null ? Set.of() : Set.copyOf(accessibleResourceIds);
    }

    /**
     * @return 组织 ID 列表（便于 SQL IN 绑定）
     */
    public List<String> organizationIdList() {
        return List.copyOf(organizationIds);
    }

    /**
     * @return 项目 ID 列表（便于 SQL IN 绑定）
     */
    public List<String> projectIdList() {
        return List.copyOf(projectIds);
    }

    /**
     * @return ACL 显式可访问资源 ID 列表（便于 SQL IN 绑定）
     */
    public List<String> accessibleResourceIdList() {
        return List.copyOf(accessibleResourceIds);
    }
}
