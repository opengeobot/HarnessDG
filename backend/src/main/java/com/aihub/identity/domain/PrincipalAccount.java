/*
 * 功能: 统一访问主体领域模型，对应 iam_principal 表的主体抽象。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.domain;

import com.aihub.shared.identity.PrincipalType;
import java.time.Instant;

/**
 * 统一访问主体。
 *
 * <p>用户、Agent 等具体身份均关联一个 Principal；用于统一主体查询与跨类型授权基线。
 *
 * @param principalId   主体业务 ID（prn_）
 * @param principalType 主体类型
 * @param displayName   展示名称
 * @param status        主体状态（ACTIVE/DISABLED）
 * @param createdAt     创建时间
 * @param updatedAt     更新时间
 */
public record PrincipalAccount(String principalId,
                               PrincipalType principalType,
                               String displayName,
                               String status,
                               Instant createdAt,
                               Instant updatedAt) {
}
