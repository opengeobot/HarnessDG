/*
 * 功能: Spring Security fail-closed 安全过滤链装配——默认拒绝、无状态、JWT 认证、统一鉴权错误。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.bootstrap.security;

import com.aihub.shared.security.TokenRevocationChecker;
import com.aihub.shared.security.TokenVerifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 安全过滤链装配。
 *
 * <p>fail-closed 原则：默认全部端点需认证；匿名仅放行登录、凭据交换、令牌刷新、最小健康检查与
 * API 文档（非生产）。无状态会话；REST/JWT API 关闭 CSRF；401/403 返回统一 {@link com.aihub.shared.api.ApiError}。
 *
 * <p>匿名放行清单：
 * <ul>
 *   <li>{@code POST /api/v1/auth/login}</li>
 *   <li>{@code /api/v1/auth/token}</li>
 *   <li>{@code /api/v1/auth/refresh}</li>
 *   <li>{@code GET /actuator/health}、{@code /actuator/health/**}</li>
 *   <li>{@code POST /api/v1/webhooks/gitea}（HMAC 签名作为认证机制）</li>
 *   <li>{@code /v3/api-docs/**}、{@code /swagger-ui/**}、{@code /swagger-ui.html}（仅非生产）</li>
 * </ul>
 */
@Configuration
public class SecurityConfiguration {

    private final TokenVerifier tokenVerifier;
    private final ObjectProvider<TokenRevocationChecker> revocationChecker;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    public SecurityConfiguration(TokenVerifier tokenVerifier,
                                 ObjectProvider<TokenRevocationChecker> revocationChecker,
                                 RestAuthenticationEntryPoint authenticationEntryPoint,
                                 RestAccessDeniedHandler accessDeniedHandler) {
        this.tokenVerifier = tokenVerifier;
        this.revocationChecker = revocationChecker;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    /**
     * @return fail-closed 安全过滤链
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationFilter jwtAuthenticationFilter =
                new JwtAuthenticationFilter(tokenVerifier, revocationChecker);

        http
                // 无状态 JWT API：关闭 CSRF（刷新 Cookie 端点另行采用 SameSite + 自定义头防护）。
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/auth/token", "/api/v1/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/gitea").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
