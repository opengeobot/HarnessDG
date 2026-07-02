/*
 * 功能: JwtTokenService 单元测试——验证 RSA 签发/校验、声明往返与失效/类型识别。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aihub.shared.error.AuthenticationException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.id.UlidIdGenerator;
import com.aihub.shared.identity.PrincipalType;
import com.aihub.shared.security.IssuedToken;
import com.aihub.shared.security.JwtClaims;
import com.aihub.shared.security.TokenIssueRequest;
import com.aihub.shared.security.TokenType;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * {@link JwtTokenService} 离线单元测试，使用测试内生成的 RSA 密钥对 PEM（无生产私钥）。
 */
class JwtTokenServiceTest {

    private static String privateKeyPem;
    private static String publicKeyPem;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        privateKeyPem = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        publicKeyPem = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    private JwtProperties properties() {
        return new JwtProperties("aihub-platform", "aihub-clients", "test-kid",
                privateKeyPem, publicKeyPem, Duration.ofMinutes(15), Duration.ofDays(7));
    }

    private JwtTokenService serviceAt(Instant now) {
        return new JwtTokenService(properties(), new UlidIdGenerator(),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void issuesAndVerifiesAccessTokenRoundTrip() {
        JwtTokenService service = serviceAt(Instant.parse("2026-06-30T00:00:00Z"));
        IssuedToken issued = service.issue(new TokenIssueRequest(
                "usr_01", PrincipalType.USER, 3L, Set.of("asset:read", "asset:write"), TokenType.ACCESS));

        assertThat(issued.token()).isNotBlank();
        JwtClaims claims = service.verify(issued.token());
        assertThat(claims.principalId()).isEqualTo("usr_01");
        assertThat(claims.principalType()).isEqualTo(PrincipalType.USER);
        assertThat(claims.tokenVersion()).isEqualTo(3L);
        assertThat(claims.scopes()).containsExactlyInAnyOrder("asset:read", "asset:write");
        assertThat(claims.tokenType()).isEqualTo(TokenType.ACCESS);
        assertThat(claims.jwtId()).startsWith("tok_");
    }

    @Test
    void rejectsTamperedToken() {
        JwtTokenService service = serviceAt(Instant.parse("2026-06-30T00:00:00Z"));
        String token = service.issue(new TokenIssueRequest(
                "usr_01", PrincipalType.USER, 0L, Set.of(), TokenType.ACCESS)).token();

        String tampered = token.substring(0, token.length() - 2) + "ab";
        assertThatThrownBy(() -> service.verify(tampered))
                .isInstanceOf(AuthenticationException.class)
                .extracting(ex -> ((AuthenticationException) ex).errorCode())
                .isEqualTo(ErrorCode.AUTH_UNAUTHENTICATED);
    }

    @Test
    void rejectsExpiredTokenWithExpiredCode() {
        Instant issueTime = Instant.parse("2026-06-30T00:00:00Z");
        String token = serviceAt(issueTime).issue(new TokenIssueRequest(
                "usr_01", PrincipalType.USER, 0L, Set.of(), TokenType.ACCESS)).token();

        // 同一密钥对、推进时钟超过 access TTL，验证过期识别。
        JwtTokenService later = serviceAt(issueTime.plus(Duration.ofMinutes(30)));
        assertThatThrownBy(() -> later.verify(token))
                .isInstanceOf(AuthenticationException.class)
                .extracting(ex -> ((AuthenticationException) ex).errorCode())
                .isEqualTo(ErrorCode.AUTH_TOKEN_EXPIRED);
    }

    @Test
    void appliesRefreshTtlForRefreshToken() {
        JwtTokenService service = serviceAt(Instant.parse("2026-06-30T00:00:00Z"));
        JwtClaims claims = service.issue(new TokenIssueRequest(
                "usr_01", PrincipalType.USER, 0L, Set.of(), TokenType.REFRESH)).claims();
        assertThat(claims.tokenType()).isEqualTo(TokenType.REFRESH);
        assertThat(Duration.between(claims.issuedAt(), claims.expiresAt())).isEqualTo(Duration.ofDays(7));
    }
}
