/*
 * 功能: 组织领域聚合，对应 organization 一行。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.organization.domain;

import java.time.Instant;

/**
 * 组织聚合。
 *
 * @param organizationId    业务组织 ID（org_）
 * @param code              组织编码（全局唯一）
 * @param name              组织名称
 * @param description       组织描述
 * @param giteaOrganization 关联的 Gitea 组织名
 * @param status            组织状态
 * @param createdBy         创建者主体 ID
 * @param createdAt         创建时间
 * @param updatedAt         更新时间
 * @param rowVersion        乐观锁版本号
 */
public record Organization(String organizationId,
                           String code,
                           String name,
                           String description,
                           String giteaOrganization,
                           OrganizationStatus status,
                           String createdBy,
                           Instant createdAt,
                           Instant updatedAt,
                           int rowVersion) {
}
