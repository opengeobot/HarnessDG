/*
 * 功能: 项目领域聚合，对应 project 一行。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.organization.domain;

import java.time.Instant;

/**
 * 项目聚合。
 *
 * @param projectId      业务项目 ID（prj_）
 * @param organizationId 所属组织 ID
 * @param code           项目编码（组织内唯一）
 * @param name           项目名称
 * @param description    项目描述
 * @param status         项目状态
 * @param createdBy      创建者主体 ID
 * @param createdAt      创建时间
 * @param updatedAt      更新时间
 * @param rowVersion     乐观锁版本号
 */
public record Project(String projectId,
                      String organizationId,
                      String code,
                      String name,
                      String description,
                      OrganizationStatus status,
                      String createdBy,
                      Instant createdAt,
                      Instant updatedAt,
                      int rowVersion) {
}
