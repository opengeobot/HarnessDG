/*
 * 功能: 外部依赖异常（HTTP 502/503）。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.shared.error;

import java.util.Map;

/**
 * 外部依赖异常，对应 HTTP 502/503（如 Gitea、MinIO 不可用）。
 */
public class DependencyException extends PlatformException {

    public DependencyException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public DependencyException(ErrorCode errorCode, String message, Map<String, Object> details, Throwable cause) {
        super(errorCode, message, details, cause);
    }
}
