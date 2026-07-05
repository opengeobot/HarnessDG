/*
 * 功能: Team 应用层视图与命令对象集合，对外只暴露视图，不返回持久化实体。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
package com.aihub.organization.application;

import com.aihub.organization.domain.Team;
import com.aihub.organization.domain.TeamMember;
import java.time.Instant;

/**
 * Team 应用层 DTO 集合。
 *
 * <p>承载用例输入命令与对外视图；字段名与 OpenAPI 契约 schema 对齐。
 */
public final class TeamDtos {

    private TeamDtos() {
    }

    /** Team 视图。 */
    public record TeamView(String teamId, String organizationId, String name,
                           String description, String status, Instant createdAt) {
        public static TeamView from(Team team) {
            return new TeamView(team.teamId(), team.organizationId(), team.name(),
                    team.description(), team.status(), team.createdAt());
        }
    }

    /** Team 成员视图。 */
    public record TeamMemberView(String teamId, String principalId, String role,
                                 Instant joinedAt) {
        public static TeamMemberView from(TeamMember member) {
            return new TeamMemberView(member.teamId(), member.principalId(),
                    member.role(), member.joinedAt());
        }
    }

    /** 创建 Team 命令。 */
    public record CreateTeamCommand(String organizationId, String name, String description) {
    }

    /** 更新 Team 命令。 */
    public record UpdateTeamCommand(String name, String description, String status) {
    }

    /** 添加 Team 成员命令。 */
    public record AddTeamMemberCommand(String teamId, String principalId, String role) {
    }
}
