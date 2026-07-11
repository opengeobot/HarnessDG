/*
 * 功能: JWT 签发契约，约定 access/refresh Token 的非对称签名签发能力。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.shared.security;

import java.time.Duration;

/**
 * JWT 签发器。
 *
 * <p>使用带 {@code kid} 的非对称签名签发 Token，私钥来源于部署 Secret。
 * 实现位于 {@code com.aihub.platform.security}，业务模块不得自行签发。
 */
public interface TokenSigner {

    /**
     * 按签发请求签发 JWT。
     *
     * @param request 签发请求（含主体、类型、Token 版本、Scope、Token 类型）
     * @return 序列化 Token 与解析后的声明
     */
    IssuedToken issue(TokenIssueRequest request);

    /**
     * 按签发请求签发 JWT，并覆盖默认有效期。
     *
     * @param request 签发请求
     * @param ttl     自定义有效期
     * @return 序列化 Token 与解析后的声明
     */
    default IssuedToken issueWithTtl(TokenIssueRequest request, Duration ttl) {
        return issue(request);
    }
}
