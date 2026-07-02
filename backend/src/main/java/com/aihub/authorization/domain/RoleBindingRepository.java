/*
 * 功能: 角色绑定仓储端口，约定主体作用域角色绑定的持久化与权限解析查询。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.authorization.domain;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 角色绑定仓储端口。
 *
 * <p>领域层只依赖本端口；实现位于 infrastructure。权限解析在数据库阶段完成（join 绑定→角色→权限），
 * 不做"先全量查再 Java 过滤"。
 */
public interface RoleBindingRepository {

    /**
     * @return 全部角色绑定
     */
    List<RoleBinding> findAll();

    Optional<RoleBinding> findByBindingId(String bindingId);

    /**
     * 判断给定主体+角色+作用域的绑定是否已存在（用于幂等/冲突判定）。
     */
    boolean exists(String principalId, String roleId, ScopeType scopeType, String scopeId);

    void create(RoleBinding binding);

    void deleteByBindingId(String bindingId);

    /**
     * 解析主体经由全部角色绑定聚合得到的有效权限编码集合（join 绑定→角色→权限，库内完成）。
     *
     * @param principalId 主体 ID
     * @return 该主体拥有的全部权限编码
     */
    Set<String> resolvePermissionCodes(String principalId);

    /**
     * @param principalId 主体 ID
     * @return 该主体被绑定的全部组织作用域 scopeId
     */
    Set<String> resolveOrganizationScopes(String principalId);

    /**
     * @param principalId 主体 ID
     * @return 该主体被绑定的全部项目作用域 scopeId
     */
    Set<String> resolveProjectScopes(String principalId);
}
