/*
 * 功能: 签发 JWT 的请求参数，描述待签发 Token 的身份与生命周期意图。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.shared.security;

import com.aihub.shared.identity.PrincipalType;
import java.util.Set;

/**
 * JWT 签发请求。
 *
 * <p>由认证应用服务在登录/凭据交换/刷新时构造；签发方根据 {@link TokenType} 套用对应有效期，
 * 并补全 {@code iss}/{@code aud}/{@code jti}/{@code iat}/{@code exp}。
 *
 * @param principalId   主体 ID（统一 {@code prn_} 前缀；JWT {@code sub} 使用此值）
 * @param principalType 主体类型
 * @param tokenVersion  主体 Token 版本
 * @param scopes        粗粒度 Scope 集合
 * @param tokenType     待签发 Token 类型
 */
public record TokenIssueRequest(String principalId,
                                PrincipalType principalType,
                                long tokenVersion,
                                Set<String> scopes,
                                TokenType tokenType) {

    public TokenIssueRequest {
        if (principalId == null || principalId.isBlank()) {
            throw new IllegalArgumentException("principalId must not be blank");
        }
        if (principalType == null) {
            throw new IllegalArgumentException("principalType must not be null");
        }
        if (tokenType == null) {
            throw new IllegalArgumentException("tokenType must not be null");
        }
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }
}
