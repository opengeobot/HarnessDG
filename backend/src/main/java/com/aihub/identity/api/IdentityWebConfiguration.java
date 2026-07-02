/*
 * 功能: identity 模块 Web 装配——注册强制改密拦截器与 Cookie 配置属性。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.api;

import com.aihub.identity.domain.LocalUserRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * identity 模块 Web 装配。
 *
 * <p>注册 {@link PasswordChangeRequiredInterceptor}（强制改密守卫）与 {@link RefreshCookieProperties}。
 * 拦截器作用于全部 {@code /api/v1/**}，内部对认证与改密端点豁免。
 */
@Configuration
@EnableConfigurationProperties(RefreshCookieProperties.class)
public class IdentityWebConfiguration implements WebMvcConfigurer {

    private final LocalUserRepository userRepository;

    public IdentityWebConfiguration(LocalUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new PasswordChangeRequiredInterceptor(userRepository))
                .addPathPatterns("/api/v1/**");
    }
}
