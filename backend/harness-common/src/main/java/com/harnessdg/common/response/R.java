/**
 * 功能：统一 API 响应封装
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.Instant;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class R<T> {

    private int code;
    private String message;
    private T data;
    private String traceId;
    private Instant timestamp;

    private R() {
        this.timestamp = Instant.now();
    }

    public static <T> R<T> ok(T data) {
        R<T> result = new R<>();
        result.code = 0;
        result.message = "success";
        result.data = data;
        return result;
    }

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> fail(int code, String message) {
        R<T> result = new R<>();
        result.code = code;
        result.message = message;
        return result;
    }

    public static <T> R<T> fail(ErrorCode errorCode) {
        return fail(errorCode.getCode(), errorCode.getMessage());
    }

    public static <T> R<T> fail(ErrorCode errorCode, String detail) {
        R<T> result = new R<>();
        result.code = errorCode.getCode();
        result.message = detail;
        return result;
    }

    public R<T> traceId(String traceId) {
        this.traceId = traceId;
        return this;
    }
}
