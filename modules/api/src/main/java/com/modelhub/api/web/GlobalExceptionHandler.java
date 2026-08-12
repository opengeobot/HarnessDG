package com.modelhub.api.web;

import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.error.ErrorCode;
import com.modelhub.shared.web.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * 全局错误 envelope（04 §2.2）：HTTP 状态码表达协议结果，code 表达稳定原因。
 * 禁止所有错误返回 200。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ErrorBody(String code, String message, List<ApiException.Detail> details, String traceId) {}

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorBody> handleApi(ApiException ex) {
        if (ex.httpStatus() >= 500) {
            log.error("api error code={}", ex.code(), ex);
        }
        return ResponseEntity.status(ex.httpStatus())
                .headers(h -> ex.headers().forEach(h::add))
                .body(body(ex.code().name(), ex.getMessage(), ex.details()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiException.Detail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiException.Detail(fe.getField(), "invalid_value"))
                .toList();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(body(ErrorCode.VALIDATION_FAILED.name(), "请求字段校验失败", details));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorBody> handleBadRequest(Exception ex) {
        return ResponseEntity.badRequest()
                .body(body(ErrorCode.VALIDATION_FAILED.name(), "请求格式错误或包含未定义字段", List.of()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorBody> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(body(ErrorCode.VALIDATION_FAILED.name(), "不支持的请求方法", List.of()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorBody> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(body(ErrorCode.FORBIDDEN.name(), "无权访问该资源", List.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> handleUnexpected(Exception ex) {
        log.error("unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body(ErrorCode.DEPENDENCY_UNAVAILABLE.name(), "服务内部错误", List.of()));
    }

    private static ErrorBody body(String code, String message, List<ApiException.Detail> details) {
        return new ErrorBody(code, message, details, TraceContext.currentTraceId());
    }
}
