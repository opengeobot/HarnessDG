/*
 * 功能: 验证 principalId/userId/agentId 的 ID 前缀语义一致性（TASK-P0BR-002）。
 * 时间: 2026-07-06
 * 作者: AxeXie
 */
package com.aihub.shared.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.aihub.platform.security.JwtProperties;
import com.aihub.platform.security.JwtTokenService;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.shared.id.UlidIdGenerator;
import com.aihub.shared.security.IssuedToken;
import com.aihub.shared.security.JwtClaims;
import com.aihub.shared.security.TokenIssueRequest;
import com.aihub.shared.security.TokenType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 验证 ID 前缀语义统一：
 * <ul>
 *   <li>{@code principalId} 统一使用 {@code prn_} 前缀（IdPrefix.PRINCIPAL）</li>
 *   <li>{@code userId} 使用 {@code usr_} 前缀（IdPrefix.USER），仅用于用户管理域</li>
 *   <li>{@code agentId} 使用 {@code agt_} 前缀（IdPrefix.AGENT），仅用于 Agent 管理域</li>
 *   <li>JWT {@code sub} 声明存储 {@code principalId}（prn_），不混用 usr_/agt_</li>
 * </ul>
 */
class PrincipalIdPrefixConsistencyTest {

    private JwtTokenService tokenService;

    @BeforeEach
    void setUp() {
        var properties = new JwtProperties("aihub-test", "aihub-test",
                null, null, null, Duration.ofMinutes(15), Duration.ofHours(24));
        IdGenerator idGenerator = new UlidIdGenerator();
        var clock = Clock.fixed(Instant.parse("2026-07-06T10:00:00Z"), ZoneOffset.UTC);
        tokenService = new JwtTokenService(properties, idGenerator, clock);
    }

    @Test
    void jwtSubUsesPrincipalIdWithPrnPrefix() {
        // Given: 用户登录后，authenticationService 用 principalId（prn_）签发 JWT
        String principalId = "prn_01J0000000000000000000001";
        TokenIssueRequest request = new TokenIssueRequest(
                principalId, PrincipalType.USER, 1L, Set.of("asset:read"), TokenType.ACCESS);

        // When: 签发 JWT
        IssuedToken issued = tokenService.issue(request);

        // Then: JWT sub 必须等于 principalId（prn_ 前缀），不得混用 usr_ 或 agt_
        JwtClaims claims = issued.claims();
        assertThat(claims.principalId()).isEqualTo(principalId);
        assertThat(claims.principalId()).startsWith("prn_");
        assertThat(claims.principalId()).doesNotStartWith("usr_");
        assertThat(claims.principalId()).doesNotStartWith("agt_");

        // 通过 verify 反解 JWT 载荷确认 sub 也是 prn_
        JwtClaims verified = tokenService.verify(issued.token());
        assertThat(verified.principalId()).startsWith("prn_");
        assertThat(verified.principalId()).isEqualTo(principalId);
    }

    @Test
    void agentJwtSubUsesPrnPrefixNotAgtPrefix() {
        // Given: Agent 登录后，authenticationService 用 principalId（prn_）签发 JWT
        String principalId = "prn_01J0000000000000000000002";
        TokenIssueRequest request = new TokenIssueRequest(
                principalId, PrincipalType.AGENT, 1L, Set.of("asset:read"), TokenType.ACCESS);

        // When: 签发 JWT
        IssuedToken issued = tokenService.issue(request);

        // Then: Agent 的 JWT sub 也是 prn_（不是 agt_），agt_ 仅用于 Agent 管理域内部标识
        JwtClaims claims = issued.claims();
        assertThat(claims.principalId()).startsWith("prn_");
        assertThat(claims.principalType()).isEqualTo(PrincipalType.AGENT);
    }

    @Test
    void idPrefixEnumDefinesCorrectPrefixes() {
        // 验证 IdPrefix 枚举定义的前缀值与 TERM-001 语义一致
        assertThat(IdPrefix.PRINCIPAL.value()).isEqualTo("prn");
        assertThat(IdPrefix.USER.value()).isEqualTo("usr");
        assertThat(IdPrefix.AGENT.value()).isEqualTo("agt");
    }

    @Test
    void principalContextCarriesPrnPrefix() {
        // PrincipalContext.principalId 必须使用 prn_ 前缀
        PrincipalContext ctx = new PrincipalContext(
                "prn_test_user", PrincipalType.USER, "subject",
                "org_01", java.util.List.of("prj_01"),
                Set.of("ADMIN"), Set.of("asset:read"),
                3, "zh-CN", "req_01", "trace01");

        assertThat(ctx.principalId()).startsWith("prn_");
        assertThat(ctx.principalType()).isEqualTo(PrincipalType.USER);
    }
}
