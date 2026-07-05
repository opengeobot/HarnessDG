/*
 * 功能: 幂等拦截器 Web 装配——注册 IdempotencyInterceptor 到 API 路径。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
package com.aihub.shared.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 幂等 Web 配置。
 *
 * <p>将 {@link IdempotencyInterceptor} 注册到所有 {@code /api/v1/**} 路径，
 * 对携带 {@code Idempotency-Key} 请求头的写请求透明复用首次结果。
 */
@Configuration
public class IdempotencyWebConfiguration implements WebMvcConfigurer {

    private final IdempotencyStore store;
    private final ObjectMapper objectMapper;

    public IdempotencyWebConfiguration(IdempotencyStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new IdempotencyInterceptor(store, objectMapper))
                .addPathPatterns("/api/v1/**");
    }
}
