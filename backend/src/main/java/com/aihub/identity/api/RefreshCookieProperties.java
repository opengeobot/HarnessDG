/*
 * 功能: 刷新令牌 Cookie 配置属性，控制 Cookie 名、Secure、SameSite 与路径。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.api;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 刷新令牌 Cookie 配置属性，绑定前缀 {@code aihub.security.cookie}。
 *
 * <p>浏览器刷新 JWT 通过 Secure/HttpOnly/SameSite Cookie 下发；访问 JWT 仅在响应体返回。
 * 本地/测试 http 环境可将 {@code secure} 置为 false。
 *
 * @param name     Cookie 名称
 * @param secure   是否仅 HTTPS 下发
 * @param sameSite SameSite 策略（Strict/Lax/None）
 * @param path     Cookie 作用路径
 */
@ConfigurationProperties(prefix = "aihub.security.cookie")
public record RefreshCookieProperties(String name, Boolean secure, String sameSite, String path) {

    public RefreshCookieProperties {
        name = (name == null || name.isBlank()) ? "aihub_refresh" : name;
        secure = secure == null ? Boolean.TRUE : secure;
        sameSite = (sameSite == null || sameSite.isBlank()) ? "Strict" : sameSite;
        path = (path == null || path.isBlank()) ? "/api/v1/auth" : path;
    }
}
