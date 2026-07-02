/*
 * 功能: 组织成员仓储端口，约定成员关系的持久化、查询与成员隔离下推查询。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.organization.domain;

import java.util.List;
import java.util.Set;

/**
 * 组织成员仓储端口。
 *
 * <p>领域层只依赖本端口；实现位于 infrastructure。按 principalId 查所属组织的作用域解析在数据库阶段完成，
 * 供 {@code AuthorizationService} 的 AccessScope 与资产等下推过滤使用。
 */
public interface OrganizationMemberRepository {

    /**
     * 列出指定组织的全部成员。
     *
     * @param organizationId 组织 ID
     */
    List<OrganizationMember> findByOrganizationId(String organizationId);

    /**
     * @param organizationId 组织 ID
     * @param principalId    主体 ID
     * @return 成员关系是否已存在
     */
    boolean exists(String organizationId, String principalId);

    /**
     * 添加成员。
     */
    void add(OrganizationMember member);

    /**
     * 移除成员。
     *
     * @return 是否实际删除了记录（false 表示成员不存在）
     */
    boolean remove(String organizationId, String principalId);

    /**
     * 成员隔离下推查询：返回该主体所属的全部组织 ID（数据库阶段过滤）。
     *
     * @param principalId 主体 ID
     * @return 主体所属组织 ID 集合
     */
    Set<String> findOrganizationIdsByPrincipal(String principalId);
}
