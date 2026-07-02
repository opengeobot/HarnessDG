/*
 * 功能: 刷新令牌 Cookie 构造辅助，负责写出与清除 Secure/HttpOnly/SameSite Cookie。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.api;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 刷新令牌 Cookie 构造辅助。
 *
 * <p>浏览器登录/刷新时通过 {@code Set-Cookie} 下发刷新 JWT：HttpOnly + Secure（可配置）+ SameSite + 受限 Path；
 * 登出时下发 Max-Age=0 的同名空 Cookie 以清除。Cookie 值即刷新 JWT，绝不写入日志。
 */
@Component
public class RefreshCookieFactory {

    private final RefreshCookieProperties properties;

    public RefreshCookieFactory(RefreshCookieProperties properties) {
        this.properties = properties;
    }

    /**
     * 构造携带刷新 JWT 的 Cookie。
     *
     * @param refreshToken   刷新 JWT
     * @param maxAgeSeconds  有效期秒数
     * @return Set-Cookie 头值
     */
    public String build(String refreshToken, long maxAgeSeconds) {
        return ResponseCookie.from(properties.name(), refreshToken)
                .httpOnly(true)
                .secure(properties.secure())
                .sameSite(properties.sameSite())
                .path(properties.path())
                .maxAge(maxAgeSeconds)
                .build()
                .toString();
    }

    /**
     * 构造用于清除刷新 Cookie 的过期空 Cookie。
     *
     * @return Set-Cookie 头值
     */
    public String clear() {
        return ResponseCookie.from(properties.name(), "")
                .httpOnly(true)
                .secure(properties.secure())
                .sameSite(properties.sameSite())
                .path(properties.path())
                .maxAge(0)
                .build()
                .toString();
    }

    /**
     * @return Cookie 名称
     */
    public String cookieName() {
        return properties.name();
    }
}
