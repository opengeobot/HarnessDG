/*
 * 功能: JWT 签名配置属性，承载签发方、受众、密钥与有效期等部署期配置。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.platform.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 签名配置属性，绑定前缀 {@code aihub.security.jwt}。
 *
 * <p>私钥/公钥以 PEM 文本注入（部署期 Secret）。当 PEM 留空时由
 * {@link JwtTokenService} 生成临时密钥对（仅供本地开发/测试），并打印告警。
 *
 * @param issuer         签发方（{@code iss}）
 * @param audience       接收方（{@code aud}）
 * @param keyId          密钥标识（{@code kid}），留空时由实现派生
 * @param privateKeyPem  RSA 私钥 PEM（PKCS#8）
 * @param publicKeyPem   RSA 公钥 PEM（X.509）
 * @param previousPublicKeyPem 轮换重叠期上一公钥 PEM（仅校验，不用于签发）
 * @param accessTokenTtl 访问令牌有效期
 * @param refreshTokenTtl 刷新令牌有效期
 */
@ConfigurationProperties(prefix = "aihub.security.jwt")
public record JwtProperties(String issuer,
                            String audience,
                            String keyId,
                            String privateKeyPem,
                            String publicKeyPem,
                            String previousPublicKeyPem,
                            Duration accessTokenTtl,
                            Duration refreshTokenTtl) {

    public JwtProperties {
        issuer = (issuer == null || issuer.isBlank()) ? "aihub-platform" : issuer;
        audience = (audience == null || audience.isBlank()) ? "aihub-clients" : audience;
        accessTokenTtl = accessTokenTtl == null ? Duration.ofMinutes(15) : accessTokenTtl;
        refreshTokenTtl = refreshTokenTtl == null ? Duration.ofDays(7) : refreshTokenTtl;
    }
}
