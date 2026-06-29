/*
 * 功能: 授权拒绝异常（HTTP 403）。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.shared.error;

import java.util.Map;

/**
 * 授权拒绝异常，对应 HTTP 403。
 */
public class AuthorizationException extends PlatformException {

    public AuthorizationException(String message) {
        super(ErrorCode.AUTH_PERMISSION_DENIED, message);
    }

    public AuthorizationException(ErrorCode errorCode, String message, Map<String, Object> details) {
        super(errorCode, message, details, null);
    }
}
