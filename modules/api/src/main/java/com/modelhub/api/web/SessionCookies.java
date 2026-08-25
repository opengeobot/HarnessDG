package com.modelhub.api.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 会话 Cookie（02 §6.1/04 §3）：mh_refresh(HttpOnly) 与 mh_csrf(SPA 可读) 必须作为
 * 两条独立的 Set-Cookie 响应头输出，禁止逗号折叠；均 Secure + SameSite=Lax。
 * Path 策略：mh_refresh 收窄至 /api/v1/auth（仅 refresh/logout 需要）；
 * mh_csrf 必须 Path=/（非 HttpOnly，SPA 页面需 document.cookie 读取做双提交，09 §11）。
 */
public final class SessionCookies {

    public static final String REFRESH_COOKIE = "mh_refresh";
    public static final String CSRF_COOKIE = "mh_csrf";

    private SessionCookies() {}

    public static void issue(HttpServletResponse response, String refreshToken, String csrfToken,
                             long maxAgeSeconds, boolean secure) {
        response.addHeader("Set-Cookie", build(REFRESH_COOKIE, refreshToken, maxAgeSeconds, true, secure, "/api/v1/auth"));
        response.addHeader("Set-Cookie", build(CSRF_COOKIE, csrfToken, maxAgeSeconds, false, secure, "/"));
    }

    public static void clear(HttpServletResponse response, boolean secure) {
        response.addHeader("Set-Cookie", build(REFRESH_COOKIE, "", 0, true, secure, "/api/v1/auth"));
        response.addHeader("Set-Cookie", build(CSRF_COOKIE, "", 0, false, secure, "/"));
    }

    private static String build(String name, String value, long maxAge, boolean httpOnly, boolean secure, String path) {
        StringBuilder sb = new StringBuilder(name).append('=').append(value)
                .append("; Path=").append(path).append("; Max-Age=").append(maxAge)
                .append("; SameSite=Lax");
        if (httpOnly) {
            sb.append("; HttpOnly");
        }
        if (secure) {
            sb.append("; Secure");
        }
        return sb.toString();
    }

    public static String readRefreshCookie(jakarta.servlet.http.HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (REFRESH_COOKIE.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}
