package com.modelhub.shared.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一成功响应 envelope（04 §2.1）：code/message/data/traceId。
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiEnvelope<T>(String code, String message, T data, String traceId) {

    public static <T> ApiEnvelope<T> ok(T data) {
        return new ApiEnvelope<>("OK", "success", data, TraceContext.currentTraceId());
    }

    public static <T> ApiEnvelope<T> created(T data) {
        return new ApiEnvelope<>("OK", "created", data, TraceContext.currentTraceId());
    }
}
