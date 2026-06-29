/*
 * 功能: 全局异常处理器，将 PlatformException 统一映射为脱敏的 ApiError 响应。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.bootstrap;

import com.aihub.shared.api.ApiError;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.PlatformException;
import com.aihub.shared.identity.PrincipalContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。
 *
 * <p>将 {@link PlatformException} 映射为绑定 HTTP 状态的 {@link ApiError}；
 * 未知异常统一归一化为 {@link ErrorCode#INTERNAL_ERROR}，不向客户端暴露堆栈、SQL 或内部 URL。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理可预期的平台业务异常。
     *
     * @param ex 平台异常
     * @return 绑定错误码 HTTP 状态的失败响应
     */
    @ExceptionHandler(PlatformException.class)
    public ResponseEntity<ApiError> handlePlatformException(PlatformException ex) {
        ErrorCode errorCode = ex.errorCode();
        ApiError body = new ApiError(
                errorCode.name(),
                ex.getMessage(),
                errorCode.defaultI18nKey(),
                ex.details(),
                errorCode.retryable(),
                requestId(),
                traceId());
        return ResponseEntity.status(errorCode.httpStatus()).body(body);
    }

    /**
     * 兜底处理未归类异常，对外仅暴露通用错误信息。
     *
     * @param ex 未知异常
     * @return 500 失败响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        LOG.error("Unhandled exception", ex);
        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        ApiError body = new ApiError(
                errorCode.name(),
                "Internal server error",
                errorCode.defaultI18nKey(),
                java.util.Map.of(),
                errorCode.retryable(),
                requestId(),
                traceId());
        return ResponseEntity.status(errorCode.httpStatus()).body(body);
    }

    private String requestId() {
        return PrincipalContextHolder.current()
                .map(context -> context.requestId())
                .orElse(null);
    }

    private String traceId() {
        return PrincipalContextHolder.current()
                .map(context -> context.traceId())
                .orElse(null);
    }
}
