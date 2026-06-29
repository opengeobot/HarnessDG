/*
 * 功能: 资源不存在异常（HTTP 404）。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.shared.error;

import java.util.Map;

/**
 * 资源不存在异常，对应 HTTP 404。亦用于私有资源防枚举场景。
 */
public class NotFoundException extends PlatformException {

    public NotFoundException(String message) {
        super(ErrorCode.ASSET_NOT_FOUND, message);
    }

    public NotFoundException(ErrorCode errorCode, String message, Map<String, Object> details) {
        super(errorCode, message, details, null);
    }
}
