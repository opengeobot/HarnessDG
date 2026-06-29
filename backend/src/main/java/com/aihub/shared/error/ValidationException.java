/*
 * 功能: 参数校验异常（HTTP 400）。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.shared.error;

import java.util.Map;

/**
 * 参数校验异常，对应 HTTP 400。
 */
public class ValidationException extends PlatformException {

    public ValidationException(String message) {
        super(ErrorCode.COMMON_INVALID_ARGUMENT, message);
    }

    public ValidationException(ErrorCode errorCode, String message, Map<String, Object> details) {
        super(errorCode, message, details, null);
    }
}
