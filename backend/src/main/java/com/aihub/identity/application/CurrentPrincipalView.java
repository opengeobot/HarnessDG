/*
 * 功能: 当前主体视图，对应契约 CurrentPrincipal；绝不包含口令哈希或凭据。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.application;

import com.aihub.shared.identity.PrincipalType;
import java.util.List;

/**
 * 当前主体视图。
 *
 * @param principalId         主体 ID（prn_）
 * @param userId              关联用户 ID（usr_，Agent 主体为 null）
 * @param principalType       主体类型
 * @param subject             外部主体标识（此处与 principalId 一致）
 * @param displayName         展示名称
 * @param organizationId      组织 ID（P0-B 暂为 null）
 * @param roles               角色集合（Task 5 前为空）
 * @param scopes              粗粒度 Scope
 * @param locale              语言偏好
 * @param forcePasswordChange 是否必须修改口令
 */
public record CurrentPrincipalView(String principalId,
                                   String userId,
                                   PrincipalType principalType,
                                   String subject,
                                   String displayName,
                                   String organizationId,
                                   List<String> roles,
                                   List<String> scopes,
                                   String locale,
                                   boolean forcePasswordChange) {
}
