/*
 * 功能: JwtAuthenticationFilter 单元测试——验证无 Token 保持匿名、有效 Token 建立主体、无效 Token 拒绝。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.bootstrap.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.aihub.shared.error.AuthenticationException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.aihub.shared.identity.PrincipalType;
import com.aihub.shared.security.JwtClaims;
import com.aihub.shared.security.TokenRevocationChecker;
import com.aihub.shared.security.TokenType;
import com.aihub.shared.security.TokenVerifier;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * {@link JwtAuthenticationFilter} 离线单元测试。
 */
class JwtAuthenticationFilterTest {

    private final TokenVerifier tokenVerifier = mock(TokenVerifier.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<TokenRevocationChecker> noRevocationChecker =
            (ObjectProvider<TokenRevocationChecker>) mock(ObjectProvider.class);

    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(tokenVerifier, noRevocationChecker);

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        PrincipalContextHolder.clear();
    }

    @Test
    void keepsAnonymousWhenNoBearerToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/assets");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] proceeded = {false};
        FilterChain chain = (req, res) -> proceeded[0] = true;

        filter.doFilter(request, response, chain);

        assertThat(proceeded[0]).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(SecurityResponseWriter.ERROR_CODE_ATTRIBUTE)).isNull();
    }

    @Test
    void establishesPrincipalForValidAccessToken() throws Exception {
        given(tokenVerifier.verify("valid")).willReturn(new JwtClaims(
                "aihub-platform", "aihub-clients", "usr_01", PrincipalType.USER, "tok_1", 1L,
                Set.of("asset:read"), Instant.now(), Instant.now().plusSeconds(900), TokenType.ACCESS));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/assets");
        request.addHeader("Authorization", "Bearer valid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            assertThat(PrincipalContextHolder.require().principalId()).isEqualTo("usr_01");
            assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("usr_01");
        };

        filter.doFilter(request, response, chain);
    }

    @Test
    void marksErrorCodeForInvalidToken() throws Exception {
        given(tokenVerifier.verify("bad"))
                .willThrow(new AuthenticationException(ErrorCode.AUTH_UNAUTHENTICATED, "invalid", java.util.Map.of()));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/assets");
        request.addHeader("Authorization", "Bearer bad");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { };

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(SecurityResponseWriter.ERROR_CODE_ATTRIBUTE))
                .isEqualTo(ErrorCode.AUTH_UNAUTHENTICATED);
    }

    @Test
    void rejectsRefreshTokenForResourceAccess() throws Exception {
        given(tokenVerifier.verify("refresh")).willReturn(new JwtClaims(
                "aihub-platform", "aihub-clients", "usr_01", PrincipalType.USER, "tok_2", 1L,
                Set.of(), Instant.now(), Instant.now().plusSeconds(900), TokenType.REFRESH));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/assets");
        request.addHeader("Authorization", "Bearer refresh");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { };

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(SecurityResponseWriter.ERROR_CODE_ATTRIBUTE))
                .isEqualTo(ErrorCode.AUTH_UNAUTHENTICATED);
    }
}
