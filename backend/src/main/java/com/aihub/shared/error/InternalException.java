/*
 * 功能: 内部错误异常（HTTP 500），对外不暴露细节。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.shared.error;

import java.util.Map;

/**
 * 内部错误异常，对应 HTTP 500。对外只暴露通用提示，不泄露内部细节。
 */
public class InternalException extends PlatformException {

    public InternalException(String message) {
        super(ErrorCode.INTERNAL_ERROR, message);
    }

    public InternalException(String message, Throwable cause) {
        super(ErrorCode.INTERNAL_ERROR, message, Map.of(), cause);
    }
}
