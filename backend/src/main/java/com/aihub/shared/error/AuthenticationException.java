/*
 * 功能: 认证失败异常（HTTP 401）。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.shared.error;

import java.util.Map;

/**
 * 认证失败异常，对应 HTTP 401。
 */
public class AuthenticationException extends PlatformException {

    public AuthenticationException(String message) {
        super(ErrorCode.AUTH_TOKEN_EXPIRED, message);
    }

    public AuthenticationException(ErrorCode errorCode, String message, Map<String, Object> details) {
        super(errorCode, message, details, null);
    }
}
