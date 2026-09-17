/*
 * 功能: Team 成员作用域查询端口（跨模块出站端口），供 authorization 等模块解析主体的 Team 作用域。
 * 时间: 2026-09-17
 * 作者: AxeXie
 */
package com.aihub.organization.application;

import java.util.Set;

/**
 * Team 成员作用域查询端口。
 *
 * <p>organization 模块向其它模块暴露的出站查询契约：按主体返回其所属 Team ID 集合。
 * authorization 模块的 {@code AuthorizationService} 据此接通 AccessScope.teamIds，使资产等查询能在
 * 数据库阶段按 Team 成员关系判定 PRIVATE 可见性的 Owner 条件。
 *
 * <p>本端口只读、无副作用；organization 不反向依赖 authorization，避免循环依赖。
 * 实现位于 organization.infrastructure，查询在数据库阶段完成（不"先全量再过滤"）。
 */
public interface TeamMembershipPort {

    /**
     * 返回主体作为成员所属的全部 Team ID（数据库阶段下推）。
     *
     * @param principalId 主体 ID
     * @return 主体所属 Team ID 集合（无所属时为空集，绝不返回 null）
     */
    Set<String> findTeamIdsByPrincipal(String principalId);
}
