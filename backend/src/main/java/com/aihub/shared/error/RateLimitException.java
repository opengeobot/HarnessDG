/*
 * 功能: 限流异常（HTTP 429）。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.shared.error;

import java.util.Map;

/**
 * 限流异常，对应 HTTP 429。
 */
public class RateLimitException extends PlatformException {

    public RateLimitException(String message) {
        super(ErrorCode.COMMON_INVALID_ARGUMENT, message);
    }

    public RateLimitException(ErrorCode errorCode, String message, Map<String, Object> details) {
        super(errorCode, message, details, null);
    }
}
