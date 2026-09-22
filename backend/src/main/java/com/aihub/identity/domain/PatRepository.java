/*
 * 功能: 个人访问令牌仓储接口。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.identity.domain;

import java.util.List;
import java.util.Optional;

/**
 * PAT 仓储。
 */
public interface PatRepository {

    void save(PersonalAccessToken pat);

    Optional<PersonalAccessToken> findByTokenId(String tokenId);

    Optional<PersonalAccessToken> findByJti(String jti);

    List<PersonalAccessToken> listByPrincipalId(String principalId);

    int revoke(String tokenId, String principalId);
}
