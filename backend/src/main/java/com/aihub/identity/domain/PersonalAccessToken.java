/*
 * 功能: 个人访问令牌（PAT）领域记录，仅存摘要不存完整 Token。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.identity.domain;

import java.time.Instant;
import java.util.Set;

/**
 * 个人访问令牌摘要。
 */
public record PersonalAccessToken(String tokenId,
                                  String jti,
                                  String principalId,
                                  String name,
                                  Set<String> scopes,
                                  PatStatus status,
                                  Instant issuedAt,
                                  Instant expiresAt,
                                  Instant lastUsedAt) {

    public boolean isActive(Instant now) {
        return status == PatStatus.ACTIVE && expiresAt.isAfter(now);
    }
}
