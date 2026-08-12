package com.modelhub.identity.service;

import com.modelhub.identity.config.IdentityProperties;
import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.error.ErrorCode;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * JWT RS256 签发与验证（02 §6.1）：只允许配置的算法与 kid，禁止 alg=none/算法降级。
 * 私钥缺失时生成临时密钥（非生产），并输出告警。
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    public static final String ISSUER = "modelhub-v1";
    public static final String AUDIENCE = "modelhub-api";

    private final IdentityProperties props;
    private final RSAKey rsaKey;
    private final JWSSigner signer;
    private final JWSVerifier verifier;

    public JwtService(IdentityProperties props) {
        this.props = props;
        try {
            if (props.jwt().privateKeyPem() != null && !props.jwt().privateKeyPem().isBlank()) {
                this.rsaKey = com.nimbusds.jose.jwk.JWK.parseFromPEMEncodedObjects(props.jwt().privateKeyPem()).toRSAKey();
            } else {
                log.warn("modelhub.identity.jwt.private-key-pem 未配置：生成临时 RSA 密钥（仅限非生产）");
                this.rsaKey = new RSAKeyGenerator(2048).keyID(props.jwt().kid()).generate();
            }
            this.signer = new RSASSASigner((RSAPrivateKey) rsaKey.toPrivateKey());
            this.verifier = new RSASSAVerifier((RSAPublicKey) rsaKey.toPublicKey());
        } catch (JOSEException e) {
            throw new IllegalStateException("JWT 密钥初始化失败", e);
        }
    }

    public String issueAccessToken(Long userId, UUID userPublicId, long authVersion, String sessionId) {
        Instant now = Instant.now();
        long ttl = props.jwt().accessTokenTtlSeconds();
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .subject(String.valueOf(userId))
                .jwtID(UUID.randomUUID().toString())
                .claim("sid", sessionId)
                .claim("auth_version", authVersion)
                .claim("scope", "web")
                .issueTime(Date.from(now))
                .notBeforeTime(Date.from(now.minusSeconds(props.jwt().clockSkewSeconds())))
                .expirationTime(Date.from(now.plusSeconds(ttl)));
        if (userPublicId != null) {
            builder.claim("uid", userPublicId.toString());
        }
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(props.jwt().kid()).build(), builder.build());
            jwt.sign(signer);
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE, "Access Token 签发失败");
        }
    }

    /** 验证并返回 claims；任何失败（签名/算法/过期/issuer）统一 UNAUTHENTICATED。 */
    public JWTClaimsSet verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (jwt.getHeader().getAlgorithm() != JWSAlgorithm.RS256) {
                throw new ApiException(ErrorCode.UNAUTHENTICATED, "不支持的 JWT 算法");
            }
            String kid = jwt.getHeader().getKeyID();
            if (kid != null && !kid.equals(props.jwt().kid())) {
                throw new ApiException(ErrorCode.UNAUTHENTICATED, "未知 kid");
            }
            if (!jwt.verify(verifier)) {
                throw new ApiException(ErrorCode.UNAUTHENTICATED, "JWT 签名校验失败");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!ISSUER.equals(claims.getIssuer())
                    || claims.getAudience() == null || !claims.getAudience().contains(AUDIENCE)) {
                throw new ApiException(ErrorCode.UNAUTHENTICATED, "JWT iss/aud 不匹配");
            }
            Date exp = claims.getExpirationTime();
            if (exp == null || exp.toInstant().isBefore(Instant.now())) {
                throw new ApiException(ErrorCode.UNAUTHENTICATED, "Access Token 已过期");
            }
            return claims;
        } catch (ParseException e) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Access Token 格式无效");
        } catch (JOSEException e) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Access Token 验证失败");
        }
    }

    /** 用于测试与调试：当前生效 kid。 */
    public String currentKid() {
        return props.jwt().kid();
    }

    public static String randomTokenUrlSafe(int byteLen) {
        byte[] buf = new byte[byteLen];
        new java.security.SecureRandom().nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
