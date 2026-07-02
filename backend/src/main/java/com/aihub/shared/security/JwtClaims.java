/*
 * 功能: JWT 声明载荷契约，承载平台粗粒度身份与 Token 元数据。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.shared.security;

import com.aihub.shared.identity.PrincipalType;
import java.time.Instant;
import java.util.Set;

/**
 * JWT 声明载荷。
 *
 * <p>仅承载身份、Token 版本与粗粒度 Scope；角色、ACL、资源状态等细粒度判断由后端
 * {@code AuthorizationService} 实时完成（本载荷不作为细粒度授权依据）。
 *
 * @param issuer        签发方（{@code iss}）
 * @param audience      接收方（{@code aud}）
 * @param principalId   主体 ID（{@code sub}，如 {@code usr_}/{@code agt_} 前缀）
 * @param principalType 主体类型
 * @param jwtId         Token 唯一标识（{@code jti}，用于吊销/重放检测）
 * @param tokenVersion  主体 Token 版本，主体侧失效（改密/禁用）后递增使旧 Token 失效
 * @param scopes        粗粒度 Scope 集合
 * @param issuedAt      签发时间（{@code iat}）
 * @param expiresAt     过期时间（{@code exp}）
 * @param tokenType     Token 类型（access / refresh）
 */
public record JwtClaims(String issuer,
                        String audience,
                        String principalId,
                        PrincipalType principalType,
                        String jwtId,
                        long tokenVersion,
                        Set<String> scopes,
                        Instant issuedAt,
                        Instant expiresAt,
                        TokenType tokenType) {

    /**
     * 紧凑构造器：对 Scope 集合做不可变拷贝，避免外部修改污染声明。
     */
    public JwtClaims {
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }
}
