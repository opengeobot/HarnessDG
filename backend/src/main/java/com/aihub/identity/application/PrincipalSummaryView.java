/*
 * 功能: 主体摘要视图，对应契约 PrincipalSummary。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.application;

import com.aihub.shared.identity.PrincipalType;

/**
 * 主体摘要视图。
 *
 * @param principalId   主体 ID（prn_）
 * @param principalType 主体类型
 * @param subject       外部主体标识
 * @param displayName   展示名称
 * @param status        状态（ACTIVE/LOCKED/DISABLED）
 */
public record PrincipalSummaryView(String principalId,
                                   PrincipalType principalType,
                                   String subject,
                                   String displayName,
                                   String status) {
}
