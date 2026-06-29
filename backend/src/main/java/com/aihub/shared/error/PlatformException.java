/*
 * 功能: 平台统一业务异常基类，携带语义错误码与结构化脱敏详情。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.shared.error;

import java.util.Collections;
import java.util.Map;

/**
 * 平台统一异常基类。
 *
 * <p>所有可预期的业务异常均继承本类并绑定 {@link ErrorCode}，由全局异常处理器统一映射为
 * {@link com.aihub.shared.api.ApiError}。{@code details} 必须是已脱敏的结构化信息。
 */
public abstract class PlatformException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Map<String, Object> details;

    protected PlatformException(ErrorCode errorCode, String message, Map<String, Object> details, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    protected PlatformException(ErrorCode errorCode, String message) {
        this(errorCode, message, null, null);
    }

    /**
     * @return 绑定的错误码
     */
    public ErrorCode errorCode() {
        return errorCode;
    }

    /**
     * @return 不可变的脱敏详情
     */
    public Map<String, Object> details() {
        return Collections.unmodifiableMap(details);
    }
}
