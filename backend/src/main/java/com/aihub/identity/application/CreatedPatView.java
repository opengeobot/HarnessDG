/*
 * 功能: PAT 创建结果视图——完整 Token 仅返回一次。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.identity.application;

import com.aihub.identity.domain.PersonalAccessToken;
import java.time.Instant;
import java.util.Set;

/**
 * PAT 创建结果。
 */
public record CreatedPatView(String tokenId,
                             String name,
                             String token,
                             Set<String> scopes,
                             Instant issuedAt,
                             Instant expiresAt) {

    public static CreatedPatView from(PersonalAccessToken pat, String rawToken) {
        return new CreatedPatView(pat.tokenId(), pat.name(), rawToken,
                pat.scopes(), pat.issuedAt(), pat.expiresAt());
    }
}
