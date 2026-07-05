/*
 * 功能: Team 成员领域值对象，对应 team_member 一行。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
package com.aihub.organization.domain;

import java.time.Instant;

/**
 * Team 成员关系。
 *
 * @param teamId      所属 Team ID
 * @param principalId 成员主体 ID
 * @param role        成员角色（OWNER/MEMBER）
 * @param createdBy   添加者主体 ID
 * @param joinedAt    加入时间
 */
public record TeamMember(String teamId,
                         String principalId,
                         String role,
                         String createdBy,
                         Instant joinedAt) {
}
