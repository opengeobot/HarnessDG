package com.modelhub.api.security;

import com.modelhub.identity.security.CurrentPrincipal;
import com.modelhub.identity.service.AuthService;
import com.modelhub.identity.service.JwtService;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Access JWT 认证过滤器：解析 Bearer Token，在线校验 auth_version/状态后注入 SecurityContext。
 * 无效 Token 不在此抛错：由后续授权规则统一返回 401/403 envelope。
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String PRINCIPAL_ATTR = "modelhub.principal";

    private final JwtService jwtService;
    private final AuthService authService;

    public JwtAuthFilter(JwtService jwtService, AuthService authService) {
        this.jwtService = jwtService;
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            try {
                JWTClaimsSet claims = jwtService.verify(auth.substring(7).trim());
                CurrentPrincipal principal = authService.resolvePrincipal(claims);
                request.setAttribute(PRINCIPAL_ATTR, principal);
                List<SimpleGrantedAuthority> authorities = principal.platformRoles().stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r.toUpperCase()))
                        .toList();
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, authorities));
            } catch (RuntimeException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
