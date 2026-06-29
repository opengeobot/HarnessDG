/*
 * 功能: 资源状态冲突异常（HTTP 409）。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.shared.error;

import java.util.Map;

/**
 * 资源状态冲突异常，对应 HTTP 409。
 */
public class ConflictException extends PlatformException {

    public ConflictException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public ConflictException(ErrorCode errorCode, String message, Map<String, Object> details) {
        super(errorCode, message, details, null);
    }
}
