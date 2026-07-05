/*
 * 功能: Team 领域聚合，对应 team 一行。从属于 Organization，成员为 Principal。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
package com.aihub.organization.domain;

import java.time.Instant;

/**
 * Team 聚合。
 *
 * @param teamId         业务 Team ID（team_）
 * @param organizationId 所属组织 ID
 * @param name           Team 名称（组织内唯一）
 * @param description    描述
 * @param status         状态（ACTIVE/DISABLED）
 * @param createdBy      创建者主体 ID
 * @param createdAt      创建时间
 * @param updatedAt      更新时间
 * @param rowVersion     乐观锁版本号
 */
public record Team(String teamId,
                   String organizationId,
                   String name,
                   String description,
                   String status,
                   String createdBy,
                   Instant createdAt,
                   Instant updatedAt,
                   int rowVersion) {

    public static Team create(String teamId, String organizationId, String name,
                              String description, String createdBy) {
        Instant now = Instant.now();
        return new Team(teamId, organizationId, name, description,
                "ACTIVE", createdBy, now, now, 1);
    }
}
