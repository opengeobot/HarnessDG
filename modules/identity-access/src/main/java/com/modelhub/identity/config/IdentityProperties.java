package com.modelhub.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 身份模块安全配置（02 §6）。密钥与允许 Origin 由部署注入，禁止硬编码默认秘密（08 §5 M1）。
 */
@ConfigurationProperties(prefix = "modelhub.identity")
public record IdentityProperties(
        Jwt jwt,
        Session session,
        RateLimit rateLimit,
        List<String> allowedOrigins,
        String clientIpHeader) {

    public IdentityProperties {
        if (jwt == null) jwt = new Jwt(null, null, 900, 60);
        if (session == null) session = new Session(604_800, 10, 5);
        if (rateLimit == null) rateLimit = new RateLimit(5, 60);
        if (allowedOrigins == null) allowedOrigins = List.of();
        if (clientIpHeader == null) clientIpHeader = "";
    }

    /** RS256 密钥（PKCS#8 私钥 PEM）与 kid；为空时启动生成临时密钥并告警（仅限非生产）。 */
    public record Jwt(String privateKeyPem, String kid, long accessTokenTtlSeconds, long clockSkewSeconds) {
        public Jwt {
            if (kid == null || kid.isBlank()) kid = "modelhub-dev-1";
            if (accessTokenTtlSeconds <= 0) accessTokenTtlSeconds = 900;
            if (clockSkewSeconds <= 0) clockSkewSeconds = 60;
        }
    }

    /** refreshTtlSeconds 默认 7 天；replayGraceSeconds 内重用旧 token 视为并发刷新而非攻击（02 §6.1）。 */
    public record Session(long refreshTtlSeconds, long replayGraceSeconds, int maxFailedAttempts) {
        public Session {
            if (refreshTtlSeconds <= 0) refreshTtlSeconds = 604_800;
            if (replayGraceSeconds < 0) replayGraceSeconds = 10;
            if (maxFailedAttempts <= 0) maxFailedAttempts = 5;
        }
    }

    /** 登录失败限流：每窗口秒内最多 maxAttempts 次（用户+IP 双维度，02 §6.2）。 */
    public record RateLimit(int maxAttempts, int windowSeconds) {
        public RateLimit {
            if (maxAttempts <= 0) maxAttempts = 5;
            if (windowSeconds <= 0) windowSeconds = 60;
        }
    }
}
