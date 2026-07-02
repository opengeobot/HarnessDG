/*
 * 功能: 角色仓储端口，约定角色与角色-权限关联的持久化与查询能力。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.authorization.domain;

import java.util.List;
import java.util.Optional;

/**
 * 角色仓储端口。
 *
 * <p>领域层只依赖本端口；实现位于 infrastructure。角色与其权限编码集合一同读写（同一事务）。
 */
public interface RoleRepository {

    /**
     * @return 全部角色（含权限集合）
     */
    List<Role> findAll();

    Optional<Role> findByRoleId(String roleId);

    Optional<Role> findByCode(String code);

    boolean existsByCode(String code);

    /**
     * 创建角色并写入其权限关联。
     */
    void create(Role role);

    /**
     * 更新角色名称/权限集合（基于 row_version 乐观锁）。
     *
     * @return 是否更新成功（false 表示并发冲突）
     */
    boolean update(Role role);

    /**
     * 删除角色及其权限关联。
     */
    void deleteByRoleId(String roleId);

    /**
     * @return 该角色当前有效绑定数量（用于删除前置校验）
     */
    long countBindingsByRoleId(String roleId);
}
