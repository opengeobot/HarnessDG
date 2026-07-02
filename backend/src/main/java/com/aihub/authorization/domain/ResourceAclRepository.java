/*
 * 功能: 资源 ACL 仓储端口，约定资源级显式授权的持久化与查询能力（含分组聚合与权限命中校验）。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.authorization.domain;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 资源 ACL 仓储端口。
 *
 * <p>领域层只依赖本端口；实现位于 infrastructure。一个 {@code aclId} 聚合同一
 * (资源类型,资源ID,主体) 的多条权限授予，底层每条权限为一行。
 */
public interface ResourceAclRepository {

    /**
     * @return 全部资源 ACL（按 aclId 分组聚合权限集合）
     */
    List<ResourceAcl> findAll();

    /**
     * 按 aclId 查找分组（含其全部权限）。
     */
    Optional<ResourceAcl> findByAclId(String aclId);

    /**
     * 判断 (资源类型,资源ID,主体,权限) 是否已存在（用于创建去重）。
     */
    boolean exists(String resourceType, String resourceId, String principalId, String permission);

    /**
     * 创建一组资源 ACL（同一 aclId、同一资源+主体的多条权限行）。
     */
    void create(ResourceAcl acl);

    /**
     * 按 aclId 删除整组权限行。
     */
    void deleteByAclId(String aclId);

    /**
     * 判断主体在指定资源上是否被显式授予某权限。
     */
    boolean hasPermission(String resourceType, String resourceId, String principalId, String permission);

    /**
     * 返回主体在某资源域上被显式授予某权限的资源 ID 集合（供 AccessScope 下推过滤）。
     */
    Set<String> accessibleResourceIds(String resourceType, String principalId, String permission);
}
