/*
 * 功能: 组织成员作用域查询端口（跨模块出站端口），供 authorization 等模块解析主体的组织作用域。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.organization.application;

import java.util.Set;

/**
 * 组织成员作用域查询端口。
 *
 * <p>organization 模块向其它模块暴露的出站查询契约：按主体返回其所属组织 ID 集合。
 * authorization 模块的 {@code AuthorizationService} 据此接通 AccessScope.organizationIds，
 * 使资产等列表查询能在数据库阶段按组织成员关系下推过滤。
 *
 * <p>本端口只读、无副作用；organization 不反向依赖 authorization，避免循环依赖。
 * 实现位于 organization.infrastructure，查询在数据库阶段完成（不"先全量再过滤"）。
 */
public interface OrganizationMembershipPort {

    /**
     * 返回主体作为成员所属的全部组织 ID（数据库阶段下推）。
     *
     * @param principalId 主体 ID
     * @return 主体所属组织 ID 集合（无所属时为空集，绝不返回 null）
     */
    Set<String> findOrganizationIdsByPrincipal(String principalId);
}
