/*
 * 功能: 角色绑定领域值对象，对应 iam_role_binding 一行。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.authorization.domain;

import java.time.Instant;

/**
 * 角色绑定。
 *
 * @param bindingId   绑定 ID（rbd_）
 * @param principalId 被授权主体 ID
 * @param roleId      角色 ID
 * @param scopeType   绑定作用域类型（PLATFORM/ORGANIZATION/PROJECT）
 * @param scopeId     作用域 ID（平台作用域为空）
 * @param createdBy   创建者主体 ID
 * @param createdAt   创建时间
 */
public record RoleBinding(String bindingId,
                          String principalId,
                          String roleId,
                          ScopeType scopeType,
                          String scopeId,
                          String createdBy,
                          Instant createdAt) {
}
