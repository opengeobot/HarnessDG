/*
 * 功能: JWT 认证过滤器，校验 Bearer access Token 并建立真实 PrincipalContext 与安全上下文。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.bootstrap.security;

import com.aihub.shared.error.AuthenticationException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.aihub.shared.logging.LogFields;
import com.aihub.shared.security.JwtClaims;
import com.aihub.shared.security.TokenRevocationChecker;
import com.aihub.shared.security.TokenType;
import com.aihub.shared.security.TokenVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 认证过滤器。
 *
 * <p>fail-closed 流程：
 * <ul>
 *   <li>无 {@code Authorization: Bearer} 头：不建立已认证主体，由 Spring Security 决定放行（匿名端点）
 *       或拒绝（受保护端点 → 401）。</li>
 *   <li>携带有效 access Token：校验签名/kid/有效期，可选执行 {@link TokenRevocationChecker}，
 *       通过后基于 Token 粗粒度声明建立真实 {@link PrincipalContext} 并写入 Spring 安全上下文；
 *       细粒度授权由后续 {@code AuthorizationService} 完成。</li>
 *   <li>Token 无效/过期/类型错误：标记错误码并清空安全上下文，由 EntryPoint 返回统一 401。</li>
 * </ul>
 *
 * <p>绝不注入"null principal + 全 scope"主体。请求关联字段（requestId/traceId/locale）由
 * {@link com.aihub.bootstrap.PrincipalContextFilter} 预先建立，本过滤器仅在认证成功时补全主体身份。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenVerifier tokenVerifier;
    private final ObjectProvider<TokenRevocationChecker> revocationChecker;

    public JwtAuthenticationFilter(TokenVerifier tokenVerifier,
                                   ObjectProvider<TokenRevocationChecker> revocationChecker) {
        this.tokenVerifier = tokenVerifier;
        this.revocationChecker = revocationChecker;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractBearerToken(request);
        if (token == null) {
            // 无凭据：保持匿名，由 Spring Security 授权阶段决定。
            filterChain.doFilter(request, response);
            return;
        }

        JwtClaims claims;
        try {
            claims = tokenVerifier.verify(token);
        } catch (AuthenticationException ex) {
            rejectAuthentication(request, ex.errorCode());
            filterChain.doFilter(request, response);
            return;
        }

        if (claims.tokenType() != TokenType.ACCESS) {
            // 仅 access Token 可用于访问资源；refresh Token 只能用于刷新端点。
            rejectAuthentication(request, ErrorCode.AUTH_UNAUTHENTICATED);
            filterChain.doFilter(request, response);
            return;
        }

        TokenRevocationChecker checker = revocationChecker.getIfAvailable();
        if (checker != null && checker.isRevoked(claims)) {
            rejectAuthentication(request, ErrorCode.AUTH_UNAUTHENTICATED);
            filterChain.doFilter(request, response);
            return;
        }

        establishPrincipal(claims);
        filterChain.doFilter(request, response);
    }

    private void establishPrincipal(JwtClaims claims) {
        PrincipalContext correlation = PrincipalContextHolder.current().orElse(null);
        String locale = correlation == null ? null : correlation.locale();
        String requestId = correlation == null ? null : correlation.requestId();
        String traceId = correlation == null ? null : correlation.traceId();

        PrincipalContext authenticated = new PrincipalContext(
                claims.principalId(),
                claims.principalType(),
                claims.principalId(),
                null,
                List.of(),
                Set.of(),
                claims.scopes(),
                0,
                locale,
                requestId,
                traceId);
        PrincipalContextHolder.set(authenticated);

        MDC.put(LogFields.PRINCIPAL_ID, claims.principalId());
        MDC.put(LogFields.PRINCIPAL_TYPE, claims.principalType().name());

        List<GrantedAuthority> authorities = claims.scopes().stream()
                .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                .collect(Collectors.toList());
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(claims.principalId(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void rejectAuthentication(HttpServletRequest request, ErrorCode errorCode) {
        SecurityContextHolder.clearContext();
        request.setAttribute(SecurityResponseWriter.ERROR_CODE_ATTRIBUTE, errorCode);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(header) || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return StringUtils.hasText(token) ? token : null;
    }
}
