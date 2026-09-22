/*
 * 功能: PAT 列表视图（不含完整 Token）。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.identity.application;

import com.aihub.identity.domain.PersonalAccessToken;
import com.aihub.identity.domain.PatStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * PAT 摘要视图。
 */
public record PatView(String tokenId,
                      String name,
                      Set<String> scopes,
                      PatStatus status,
                      Instant issuedAt,
                      Instant expiresAt,
                      Instant lastUsedAt) {

    public static PatView from(PersonalAccessToken pat) {
        return new PatView(pat.tokenId(), pat.name(), pat.scopes(),
                pat.status(), pat.issuedAt(), pat.expiresAt(), pat.lastUsedAt());
    }

    public static List<PatView> fromList(List<PersonalAccessToken> pats) {
        return pats.stream().map(PatView::from).toList();
    }
}
