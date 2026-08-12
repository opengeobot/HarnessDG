package com.modelhub.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modelhub.shared.web.ApiEnvelope;
import com.modelhub.shared.web.TraceContext;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Map;

/**
 * 安全配置：无状态 JWT；register/login/refresh/logout 与健康检查匿名可达。
 * 目录只读端点（列表/详情/resolve/resource-types/metadata options）匿名 GET 可达，
 * 携带有效 Token 时仍会解析主体以获得个性化可见范围（02 §4/§5）。
 * refresh/logout 的 Cookie+CSRF+Origin 三匹配在 AuthController 内执行（02 §6.1）。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter,
                                           ObjectMapper objectMapper) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/v1/livez", "/api/v1/readyz").permitAll()
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/repositories",
                                "/api/v1/repositories/*",
                                "/api/v1/repositories/*/related",
                                "/api/v1/repositories/*/branches",
                                "/api/v1/repositories/*/commits",
                                "/api/v1/repositories/*/files",
                                "/api/v1/repositories/resolve/**",
                                "/api/v1/resource-types/**",
                                "/api/v1/metadata/options",
                                "/api/v1/downloads/*/content").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/repositories/*/files/*/download-sessions").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint((request, response, ex) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                            "code", "UNAUTHENTICATED",
                            "message", "未认证或凭证已过期",
                            "traceId", TraceContext.currentTraceId())));
                }))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
