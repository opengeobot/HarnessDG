/*
 * 功能: JWT 校验契约，约定签名/kid/有效期校验与声明解析能力。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.shared.security;

/**
 * JWT 校验器。
 *
 * <p>校验签名、{@code kid}、{@code iss}/{@code aud} 与有效期，解析为 {@link JwtClaims}。
 * 仅做 Token 自身的密码学与结构校验；主体级吊销（tokenVersion/禁用）由
 * {@link TokenRevocationChecker} 在身份表就绪后补充。
 */
public interface TokenVerifier {

    /**
     * 校验并解析紧凑 JWT。
     *
     * @param token 序列化 JWT 紧凑串
     * @return 解析后的声明
     * @throws com.aihub.shared.error.AuthenticationException 当 Token 缺失签名信任、被篡改或已过期时
     */
    JwtClaims verify(String token);
}
