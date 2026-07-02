/*
 * 功能: 权限定义仓储端口，约定权限清单查询能力。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.authorization.domain;

import java.util.List;

/**
 * 权限定义仓储端口。
 *
 * <p>领域层只依赖本端口；实现位于 infrastructure。
 */
public interface PermissionRepository {

    /**
     * @return 全部权限定义（按编码排序）
     */
    List<PermissionDefinition> findAll();

    /**
     * 校验给定权限编码是否全部存在。
     *
     * @param codes 待校验的权限编码
     * @return 不存在的权限编码集合（为空表示全部存在）
     */
    List<String> findUnknownCodes(List<String> codes);
}
