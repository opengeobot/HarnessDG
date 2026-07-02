/*
 * 功能: 基于 JJWT 的 JWT 签发/校验实现，使用 RSA 非对称签名并在头部携带 kid。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.platform.security;

import com.aihub.shared.error.AuthenticationException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.shared.identity.PrincipalType;
import com.aihub.shared.security.IssuedToken;
import com.aihub.shared.security.JwtClaims;
import com.aihub.shared.security.TokenIssueRequest;
import com.aihub.shared.security.TokenSigner;
import com.aihub.shared.security.TokenType;
import com.aihub.shared.security.TokenVerifier;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 JJWT 的 {@link TokenSigner} 与 {@link TokenVerifier} 实现。
 *
 * <p>使用 RSA-256（RS256）非对称签名，JWT 头携带 {@code kid}；access/refresh 默认有效期由
 * {@link JwtProperties} 提供并可覆盖。当部署未注入 PEM 密钥时生成临时密钥对（仅供本地/测试），
 * 并打印告警。私钥永不进入日志。
 */
public class JwtTokenService implements TokenSigner, TokenVerifier {

    private static final Logger LOG = LoggerFactory.getLogger(JwtTokenService.class);
    private static final String PRINCIPAL_TYPE_CLAIM = "principalType";
    private static final String TOKEN_VERSION_CLAIM = "tokenVersion";
    private static final String SCOPE_CLAIM = "scope";
    private static final String TOKEN_TYPE_CLAIM = "tokenType";

    private final JwtProperties properties;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final String keyId;

    public JwtTokenService(JwtProperties properties, IdGenerator idGenerator, Clock clock) {
        this.properties = properties;
        this.idGenerator = idGenerator;
        this.clock = clock;
        KeyPair keyPair = resolveKeyPair(properties);
        this.privateKey = keyPair.getPrivate();
        this.publicKey = keyPair.getPublic();
        this.keyId = resolveKeyId(properties, this.publicKey);
    }

    @Override
    public IssuedToken issue(TokenIssueRequest request) {
        Instant now = clock.instant();
        Duration ttl = request.tokenType() == TokenType.REFRESH
                ? properties.refreshTokenTtl()
                : properties.accessTokenTtl();
        Instant expiresAt = now.plus(ttl);
        String jwtId = idGenerator.generate(IdPrefix.TOKEN);

        Set<String> scopes = new LinkedHashSet<>(request.scopes());
        String token = Jwts.builder()
                .header().keyId(keyId).and()
                .issuer(properties.issuer())
                .audience().add(properties.audience()).and()
                .subject(request.principalId())
                .id(jwtId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(PRINCIPAL_TYPE_CLAIM, request.principalType().name())
                .claim(TOKEN_VERSION_CLAIM, request.tokenVersion())
                .claim(SCOPE_CLAIM, String.join(" ", scopes))
                .claim(TOKEN_TYPE_CLAIM, request.tokenType().name())
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();

        JwtClaims claims = new JwtClaims(properties.issuer(), properties.audience(),
                request.principalId(), request.principalType(), jwtId, request.tokenVersion(),
                scopes, now, expiresAt, request.tokenType());
        return new IssuedToken(token, claims);
    }

    @Override
    public JwtClaims verify(String token) {
        if (token == null || token.isBlank()) {
            throw new AuthenticationException(ErrorCode.AUTH_UNAUTHENTICATED, "Missing token", Map.of());
        }
        try {
            Claims payload = Jwts.parser()
                    .verifyWith(publicKey)
                    .requireIssuer(properties.issuer())
                    .requireAudience(properties.audience())
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return toClaims(payload);
        } catch (ExpiredJwtException ex) {
            throw new AuthenticationException(ErrorCode.AUTH_TOKEN_EXPIRED, "Token expired", Map.of());
        } catch (JwtException | IllegalArgumentException ex) {
            // 不记录 Token 内容，避免敏感信息落盘。
            throw new AuthenticationException(ErrorCode.AUTH_UNAUTHENTICATED, "Invalid token", Map.of());
        }
    }

    private JwtClaims toClaims(Claims payload) {
        PrincipalType principalType = parsePrincipalType(payload.get(PRINCIPAL_TYPE_CLAIM, String.class));
        long tokenVersion = payload.get(TOKEN_VERSION_CLAIM, Number.class) == null
                ? 0L
                : payload.get(TOKEN_VERSION_CLAIM, Number.class).longValue();
        Set<String> scopes = parseScopes(payload.get(SCOPE_CLAIM, String.class));
        TokenType tokenType = parseTokenType(payload.get(TOKEN_TYPE_CLAIM, String.class));
        return new JwtClaims(payload.getIssuer(), audienceOf(payload), payload.getSubject(),
                principalType, payload.getId(), tokenVersion, scopes,
                payload.getIssuedAt() == null ? null : payload.getIssuedAt().toInstant(),
                payload.getExpiration() == null ? null : payload.getExpiration().toInstant(),
                tokenType);
    }

    private String audienceOf(Claims payload) {
        Set<String> audiences = payload.getAudience();
        if (audiences == null || audiences.isEmpty()) {
            return properties.audience();
        }
        return audiences.iterator().next();
    }

    private PrincipalType parsePrincipalType(String value) {
        if (value == null) {
            throw new AuthenticationException(ErrorCode.AUTH_UNAUTHENTICATED, "Missing principalType", Map.of());
        }
        try {
            return PrincipalType.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new AuthenticationException(ErrorCode.AUTH_UNAUTHENTICATED, "Unknown principalType", Map.of());
        }
    }

    private TokenType parseTokenType(String value) {
        if (value == null) {
            return TokenType.ACCESS;
        }
        try {
            return TokenType.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return TokenType.ACCESS;
        }
    }

    private Set<String> parseScopes(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return new LinkedHashSet<>(List.of(value.trim().split("\\s+")));
    }

    private static KeyPair resolveKeyPair(JwtProperties properties) {
        boolean hasPrivate = properties.privateKeyPem() != null && !properties.privateKeyPem().isBlank();
        boolean hasPublic = properties.publicKeyPem() != null && !properties.publicKeyPem().isBlank();
        if (hasPrivate && hasPublic) {
            return new KeyPair(loadPublicKey(properties.publicKeyPem()),
                    loadPrivateKey(properties.privateKeyPem()));
        }
        LOG.warn("JWT signing keys are not configured (aihub.security.jwt.*-key-pem); "
                + "generating an ephemeral RSA key pair for non-production use only. "
                + "Configure deployment secrets before production.");
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("RSA key pair generation failed", ex);
        }
    }

    private static PrivateKey loadPrivateKey(String pem) {
        try {
            byte[] der = Base64.getDecoder().decode(stripPem(pem));
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid RSA private key PEM", ex);
        }
    }

    private static PublicKey loadPublicKey(String pem) {
        try {
            byte[] der = Base64.getDecoder().decode(stripPem(pem));
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid RSA public key PEM", ex);
        }
    }

    private static String stripPem(String pem) {
        return pem.replaceAll("-----BEGIN (?:RSA )?(?:PRIVATE|PUBLIC) KEY-----", "")
                .replaceAll("-----END (?:RSA )?(?:PRIVATE|PUBLIC) KEY-----", "")
                .replaceAll("\\s", "");
    }

    private static String resolveKeyId(JwtProperties properties, PublicKey publicKey) {
        if (properties.keyId() != null && !properties.keyId().isBlank()) {
            return properties.keyId();
        }
        // 由公钥指纹派生稳定 kid，避免泄露密钥内容。
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded());
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException ex) {
            return "default";
        }
    }
}
