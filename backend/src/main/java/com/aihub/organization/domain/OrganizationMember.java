/*
 * 功能: 组织成员领域值对象，对应 organization_member 一行。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.organization.domain;

import java.time.Instant;

/**
 * 组织成员关系。
 *
 * <p>以 (organizationId, principalId) 为业务标识；不暴露 BIGINT 内部主键。
 *
 * @param organizationId 所属组织 ID
 * @param principalId    成员主体 ID
 * @param role           成员角色
 * @param createdBy      创建者主体 ID
 * @param createdAt      加入时间
 */
public record OrganizationMember(String organizationId,
                                 String principalId,
                                 MemberRole role,
                                 String createdBy,
                                 Instant createdAt) {
}
