package com.modelhub.shared.error;

import java.util.List;
import java.util.Map;

/**
 * 业务异常：携带稳定 code、HTTP 状态与字段级 details（04 §2.2）。
 */
public class ApiException extends RuntimeException {

    public record Detail(String field, String reason) {}

    private final ErrorCode code;
    private final Integer statusOverride;
    private final List<Detail> details;
    private final Map<String, String> headers;

    public ApiException(ErrorCode code, String message) {
        this(code, message, List.of(), Map.of(), null);
    }

    public ApiException(ErrorCode code, String message, List<Detail> details) {
        this(code, message, details, Map.of(), null);
    }

    public ApiException(ErrorCode code, String message, List<Detail> details, Map<String, String> headers) {
        this(code, message, details, headers, null);
    }

    public ApiException(ErrorCode code, String message, List<Detail> details, Map<String, String> headers,
                        Integer statusOverride) {
        super(message);
        this.code = code;
        this.statusOverride = statusOverride;
        this.details = details == null ? List.of() : List.copyOf(details);
        this.headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    public ErrorCode code() {
        return code;
    }

    public int httpStatus() {
        return statusOverride != null ? statusOverride : code.httpStatus();
    }

    public List<Detail> details() {
        return details;
    }

    public Map<String, String> headers() {
        return headers;
    }

    public static ApiException of(ErrorCode code, String message) {
        return new ApiException(code, message);
    }

    /** 查询参数格式错误（04 §2.2：400），code 仍使用 VALIDATION_FAILED。 */
    public static ApiException badRequest(String message, List<Detail> details) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message, details, Map.of(), 400);
    }
}
