/**
 * 功能：全局异常处理器，统一错误响应格式
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.common.exception;

import com.harnessdg.common.log.TraceContext;
import com.harnessdg.common.response.ErrorCode;
import com.harnessdg.common.response.R;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleBizException(BizException ex) {
        log.warn("Business exception: code={}, message={}", ex.getErrorCode().getCode(), ex.getMessage());
        return R.<Void>fail(ex.getErrorCode(), ex.getMessage())
                .traceId(TraceContext.getTraceId());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleValidationException(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", detail);
        return R.<Void>fail(ErrorCode.VALIDATION_ERROR, detail)
                .traceId(TraceContext.getTraceId());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("Constraint violation: {}", ex.getMessage());
        return R.<Void>fail(ErrorCode.VALIDATION_ERROR, ex.getMessage())
                .traceId(TraceContext.getTraceId());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = String.format("Invalid parameter '%s': %s", ex.getName(), ex.getValue());
        log.warn("Type mismatch: {}", message);
        return R.<Void>fail(ErrorCode.BAD_REQUEST, message)
                .traceId(TraceContext.getTraceId());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleUnexpectedException(Exception ex) {
        log.error("Unexpected exception", ex);
        // 临时返回详细错误信息用于调试
        return R.<Void>fail(ErrorCode.INTERNAL_ERROR, ex.getMessage())
                .traceId(TraceContext.getTraceId());
    }
}
