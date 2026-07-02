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

    /** 未认证或凭据无效（缺失/伪造/签名校验失败 Token）。 */
    AUTH_UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "error.auth.unauthenticated", false, AlertLevel.INFO),

    /** 无访问权限。 */
    AUTH_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "error.auth.permissionDenied", false, AlertLevel.INFO),

    /** 用户名或口令错误（登录失败，不区分以防枚举）。 */
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "error.auth.invalidCredentials", false, AlertLevel.INFO),

    /** 账户因连续登录失败被锁定。 */
    AUTH_ACCOUNT_LOCKED(HttpStatus.LOCKED, "error.auth.accountLocked", false, AlertLevel.WARN),

    /** 账户已禁用。 */
    AUTH_ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "error.auth.accountDisabled", false, AlertLevel.WARN),

    /** 刷新令牌重放被拒（已轮换/已吊销的刷新令牌被再次使用，整族吊销）。 */
    AUTH_REFRESH_REPLAYED(HttpStatus.UNAUTHORIZED, "error.auth.refreshReplayed", false, AlertLevel.WARN),

    /** 必须先修改口令才能访问其他受保护资源。 */
    PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "error.auth.passwordChangeRequired", false, AlertLevel.INFO),

    /** 新口令不满足强度策略。 */
    PASSWORD_POLICY_VIOLATION(HttpStatus.BAD_REQUEST, "error.auth.passwordPolicyViolation", false, AlertLevel.NONE),

    /** 用户名已存在。 */
    USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "error.user.alreadyExists", false, AlertLevel.NONE),

    /** 用户不存在。 */
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "error.user.notFound", false, AlertLevel.NONE),

    /** Agent 不存在。 */
    AGENT_NOT_FOUND(HttpStatus.NOT_FOUND, "error.agent.notFound", false, AlertLevel.NONE),

    /** 角色不存在。 */
    ROLE_NOT_FOUND(HttpStatus.NOT_FOUND, "error.role.notFound", false, AlertLevel.NONE),

    /** 角色编码已存在。 */
    ROLE_ALREADY_EXISTS(HttpStatus.CONFLICT, "error.role.alreadyExists", false, AlertLevel.NONE),

    /** 内置角色不可改名/删除。 */
    ROLE_BUILTIN_IMMUTABLE(HttpStatus.CONFLICT, "error.role.builtinImmutable", false, AlertLevel.NONE),

    /** 角色仍被绑定引用，不可删除。 */
    ROLE_IN_USE(HttpStatus.CONFLICT, "error.role.inUse", false, AlertLevel.NONE),

    /** 引用了未注册的权限编码。 */
    PERMISSION_UNKNOWN(HttpStatus.BAD_REQUEST, "error.permission.unknown", false, AlertLevel.NONE),

    /** 角色绑定不存在。 */
    ROLE_BINDING_NOT_FOUND(HttpStatus.NOT_FOUND, "error.roleBinding.notFound", false, AlertLevel.NONE),

    /** 角色绑定已存在。 */
    ROLE_BINDING_ALREADY_EXISTS(HttpStatus.CONFLICT, "error.roleBinding.alreadyExists", false, AlertLevel.NONE),

    /** 资源 ACL 不存在。 */
    RESOURCE_ACL_NOT_FOUND(HttpStatus.NOT_FOUND, "error.resourceAcl.notFound", false, AlertLevel.NONE),

    /** 资源 ACL 已存在。 */
    RESOURCE_ACL_ALREADY_EXISTS(HttpStatus.CONFLICT, "error.resourceAcl.alreadyExists", false, AlertLevel.NONE),

    /** 组织不存在（亦用于非成员访问组织资源的防枚举）。 */
    ORGANIZATION_NOT_FOUND(HttpStatus.NOT_FOUND, "error.organization.notFound", false, AlertLevel.NONE),

    /** 组织编码已存在。 */
    ORGANIZATION_ALREADY_EXISTS(HttpStatus.CONFLICT, "error.organization.alreadyExists", false, AlertLevel.NONE),

    /** 组织成员不存在。 */
    ORGANIZATION_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "error.organization.memberNotFound", false, AlertLevel.NONE),

    /** 组织成员已存在。 */
    ORGANIZATION_MEMBER_ALREADY_EXISTS(HttpStatus.CONFLICT, "error.organization.memberAlreadyExists",
            false, AlertLevel.NONE),

    /** 项目不存在。 */
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "error.project.notFound", false, AlertLevel.NONE),

    /** 项目编码在组织内已存在。 */
    PROJECT_ALREADY_EXISTS(HttpStatus.CONFLICT, "error.project.alreadyExists", false, AlertLevel.NONE),

    /** 访问主体不存在。 */
    PRINCIPAL_NOT_FOUND(HttpStatus.NOT_FOUND, "error.principal.notFound", false, AlertLevel.NONE),

    /** 资源被并发修改（乐观锁冲突，可重试）。 */
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "error.common.concurrentModification", true, AlertLevel.NONE),

    /** 字典项不存在。 */
    DICTIONARY_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "error.dictionary.itemNotFound", false, AlertLevel.NONE),

    /** 字典项在该字典下已存在。 */
    DICTIONARY_ITEM_ALREADY_EXISTS(HttpStatus.CONFLICT, "error.dictionary.itemAlreadyExists", false, AlertLevel.NONE),

    /** 治理字段引用了未知或已停用的字典项（拒绝新建引用）。 */
    DICTIONARY_VALUE_INVALID(HttpStatus.BAD_REQUEST, "error.dictionary.valueInvalid", false, AlertLevel.NONE),

    /** 受控标签不存在。 */
    TAG_NOT_FOUND(HttpStatus.NOT_FOUND, "error.tag.notFound", false, AlertLevel.NONE),

    /** 受控标签在该作用域下已存在。 */
    TAG_ALREADY_EXISTS(HttpStatus.CONFLICT, "error.tag.alreadyExists", false, AlertLevel.NONE),

    /** 引用了未登记/已停用/作用域不允许的标签（拒绝自由标签）。 */
    TAG_VALUE_INVALID(HttpStatus.BAD_REQUEST, "error.tag.valueInvalid", false, AlertLevel.NONE),

    /** 配置项不存在。 */
    CONFIG_NOT_FOUND(HttpStatus.NOT_FOUND, "error.config.notFound", false, AlertLevel.NONE),

    /** 配置键命中敏感模式，禁止写入（密码/Token/私钥/凭据等）。 */
    CONFIG_SECRET_FORBIDDEN(HttpStatus.BAD_REQUEST, "error.config.secretForbidden", false, AlertLevel.WARN),

    /** 配置提交值不满足类型或校验器要求。 */
    CONFIG_VALUE_INVALID(HttpStatus.BAD_REQUEST, "error.config.valueInvalid", false, AlertLevel.NONE),

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

    /** 任务不存在。 */
    JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "error.job.notFound", false, AlertLevel.NONE),

    /** 任务当前状态不允许该操作。 */
    JOB_STATE_NOT_ALLOWED(HttpStatus.CONFLICT, "error.job.stateNotAllowed", false, AlertLevel.INFO),

    /** 通知不存在或不属于当前主体。 */
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "error.notification.notFound", false, AlertLevel.NONE),

    /** 幂等键冲突（同键不同请求体）。 */
    IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "error.idempotency.keyConflict", false, AlertLevel.NONE),

    /** Webhook 投递目标地址不安全（内网/保留地址被 SSRF 防护拒绝）。 */
    WEBHOOK_TARGET_FORBIDDEN(HttpStatus.BAD_REQUEST, "error.webhook.targetForbidden", false, AlertLevel.WARN),

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
