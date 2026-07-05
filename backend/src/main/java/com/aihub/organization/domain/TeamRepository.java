/*
 * 功能: Team 仓储端口，约定 Team 聚合与成员的持久化与查询。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
package com.aihub.organization.domain;

import java.util.List;
import java.util.Optional;

/**
 * Team 仓储端口。
 *
 * <p>领域层只依赖本端口；实现位于 infrastructure。
 */
public interface TeamRepository {

    Optional<Team> findByTeamId(String teamId);

    List<Team> findByOrganizationId(String organizationId);

    boolean existsByName(String organizationId, String name);

    void insert(Team team);

    void update(Team team);

    // ---- 成员操作 ----

    List<TeamMember> findMembersByTeamId(String teamId);

    Optional<TeamMember> findMember(String teamId, String principalId);

    void addMember(TeamMember member);

    void removeMember(String teamId, String principalId);
}
