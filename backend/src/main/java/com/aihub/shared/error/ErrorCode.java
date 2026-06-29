/*
 * 功能: 集中定义平台语义错误码及其 HTTP 状态、国际化键、可重试性与告警等级。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.shared.error;

import org.springframework.http.HttpStatus;

/**
 * 平台错误码集中定义。
 *
 * <p>每个错误码绑定固定的 HTTP 状态、默认国际化键、是否可重试与告警等级，
 * 避免在各模块散落硬编码。Controller / MCP Tool 不得吞掉异常返回伪成功。
 */
public enum ErrorCode {

    /** 通用参数非法。 */
    COMMON_INVALID_ARGUMENT(HttpStatus.BAD_REQUEST, "error.common.invalidArgument", false, AlertLevel.NONE),

    /** 认证 Token 已过期。 */
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "error.auth.tokenExpired", false, AlertLevel.INFO),

    /** 无访问权限。 */
    AUTH_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "error.auth.permissionDenied", false, AlertLevel.INFO),

    /** 资产不存在（亦用于私有资源防枚举）。 */
    ASSET_NOT_FOUND(HttpStatus.NOT_FOUND, "error.asset.notFound", false, AlertLevel.NONE),

    /** 资产坐标已存在（namespace+type+name 冲突）。 */
    ASSET_ALREADY_EXISTS(HttpStatus.CONFLICT, "error.asset.alreadyExists", false, AlertLevel.NONE),

    /** 资产仓库开通失败（Gitea 依赖异常，可重试）。 */
    ASSET_REPOSITORY_PROVISION_FAILED(HttpStatus.BAD_GATEWAY, "error.asset.repositoryProvisionFailed",
            true, AlertLevel.WARN),

    /** 资产被并发修改（乐观锁冲突，可重试）。 */
    ASSET_CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "error.asset.concurrentModification",
            true, AlertLevel.NONE),

    /** 资产版本冲突，目标版本已存在。 */
    ASSET_VERSION_CONFLICT(HttpStatus.CONFLICT, "error.asset.versionConflict", false, AlertLevel.INFO),

    /** 当前版本状态不允许该操作。 */
    VERSION_STATE_NOT_ALLOWED(HttpStatus.CONFLICT, "error.version.stateNotAllowed", false, AlertLevel.INFO),

    /** 上传会话已过期。 */
    UPLOAD_SESSION_EXPIRED(HttpStatus.CONFLICT, "error.upload.sessionExpired", false, AlertLevel.INFO),

    /** DVC 对象缺失。 */
    DVC_OBJECT_MISSING(HttpStatus.NOT_FOUND, "error.dvc.objectMissing", false, AlertLevel.WARN),

    /** Gitea 依赖不可用。 */
    GITEA_DEPENDENCY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "error.dependency.gitea", true, AlertLevel.CRITICAL),

    /** MinIO 依赖不可用。 */
    MINIO_DEPENDENCY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "error.dependency.minio", true, AlertLevel.CRITICAL),

    /** MCP 工具不在白名单内。 */
    MCP_TOOL_NOT_ALLOWED(HttpStatus.FORBIDDEN, "error.mcp.toolNotAllowed", false, AlertLevel.WARN),

    /** 任务重试次数耗尽。 */
    JOB_RETRY_EXHAUSTED(HttpStatus.INTERNAL_SERVER_ERROR, "error.job.retryExhausted", false, AlertLevel.CRITICAL),

    /** 未归类的内部错误，对外不暴露细节。 */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "error.common.internal", false, AlertLevel.CRITICAL);

    private final HttpStatus httpStatus;
    private final String defaultI18nKey;
    private final boolean retryable;
    private final AlertLevel alertLevel;

    ErrorCode(HttpStatus httpStatus, String defaultI18nKey, boolean retryable, AlertLevel alertLevel) {
        this.httpStatus = httpStatus;
        this.defaultI18nKey = defaultI18nKey;
        this.retryable = retryable;
        this.alertLevel = alertLevel;
    }

    /**
     * @return 该错误码对应的 HTTP 状态
     */
    public HttpStatus httpStatus() {
        return httpStatus;
    }

    /**
     * @return 默认国际化键
     */
    public String defaultI18nKey() {
        return defaultI18nKey;
    }

    /**
     * @return 是否可由调用方安全重试
     */
    public boolean retryable() {
        return retryable;
    }

    /**
     * @return 告警等级
     */
    public AlertLevel alertLevel() {
        return alertLevel;
    }
}
