/*
 * 功能: 组织仓储端口，约定组织聚合的持久化与查询（含成员隔离下推查询）。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.organization.domain;

import java.util.List;
import java.util.Optional;

/**
 * 组织仓储端口。
 *
 * <p>领域层只依赖本端口；实现位于 infrastructure。成员隔离列表查询在数据库阶段下推过滤，
 * 不做"先全量查再 Java 过滤"。
 */
public interface OrganizationRepository {

    /**
     * @param organizationId 业务组织 ID
     * @return 组织聚合（不存在返回空）
     */
    Optional<Organization> findByOrganizationId(String organizationId);

    /**
     * 列出全部组织（供平台组织管理员查看）。
     */
    List<Organization> findAll();

    /**
     * 成员隔离下推查询：返回该主体作为成员所属的全部组织（数据库阶段过滤）。
     *
     * @param principalId 主体 ID
     * @return 主体所属组织集合
     */
    List<Organization> findByPrincipalMembership(String principalId);

    /**
     * @param code 组织编码
     * @return 编码是否已被占用
     */
    boolean existsByCode(String code);

    /**
     * @param organizationId 业务组织 ID
     * @return 组织是否存在
     */
    boolean existsByOrganizationId(String organizationId);

    /**
     * 持久化新组织。
     */
    void insert(Organization organization);
}
