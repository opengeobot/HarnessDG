package com.modelhub.shared.error;

/**
 * 稳定错误 code 基线（04 §2.2）。
 * OpenAPI 使用可扩展枚举提示，客户端必须容忍未知 code。
 */
public enum ErrorCode {
    UNAUTHENTICATED(401),
    CSRF_INVALID(401),
    FORBIDDEN(403),
    RESOURCE_NOT_FOUND(404),
    CONFLICT(409),
    IDEMPOTENCY_CONFLICT(409),
    INVALID_STATE_TRANSITION(409),
    PRECONDITION_FAILED(412),
    VALIDATION_FAILED(422),
    METADATA_SCHEMA_INVALID(422),
    RATE_LIMITED(429),
    DEPENDENCY_UNAVAILABLE(503),
    FEATURE_DISABLED(403),
    EVENT_HISTORY_EXPIRED(410),
    CONTENT_REJECTED(422);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
