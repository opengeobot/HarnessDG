/*
 * 功能: Team API 请求体集合，字段名与 OpenAPI 契约 schema 对齐。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
package com.aihub.organization.api;

/**
 * Team API 请求体集合。
 */
public final class TeamRequests {

    private TeamRequests() {
    }

    /** 创建 Team 请求。 */
    public record CreateTeamRequest(String name, String description) {
    }

    /** 更新 Team 请求。 */
    public record UpdateTeamRequest(String name, String description, String status) {
    }

    /** 添加 Team 成员请求。 */
    public record AddTeamMemberRequest(String principalId, String role) {
    }
}
