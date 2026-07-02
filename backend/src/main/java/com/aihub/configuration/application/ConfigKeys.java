/*
 * 功能: 统一配置键常量，避免其它模块散落字符串 key；与本迁移预置的 system_config.config_key 一致。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.configuration.application;

/**
 * 统一配置键常量。
 *
 * <p>集中预置配置键，供 {@link PlatformConfigService} 等模块按类型安全方式读取，避免散落硬编码字符串。
 * 常量值必须与 V8 迁移预置的 {@code system_config.config_key} 一致。
 */
public final class ConfigKeys {

    private ConfigKeys() {
    }

    /** Web 直传单会话最大字节数（LONG）。 */
    public static final String TRANSFER_WEB_MAX_SESSION_BYTES = "transfer.web.maxSessionBytes";
    /** 预签名 URL 有效期（秒，INTEGER）。 */
    public static final String TRANSFER_PRESIGNED_URL_TTL_SECONDS = "transfer.presignedUrl.ttlSeconds";
    /** 发布版本是否要求审批（BOOLEAN，不可热更新）。 */
    public static final String VERSION_PUBLISH_REQUIRE_APPROVAL = "version.publish.requireApproval";
    /** DVC 任务最大重试次数（INTEGER）。 */
    public static final String JOB_DVC_MAX_RETRIES = "job.dvc.maxRetries";
    /** Webhook 任务最大重试次数（INTEGER）。 */
    public static final String JOB_WEBHOOK_MAX_RETRIES = "job.webhook.maxRetries";
    /** MCP 工具单次返回最大条目数（INTEGER）。 */
    public static final String MCP_MAX_RESULT_ITEMS = "mcp.maxResultItems";
    /** 是否启用 MCP 写工具（BOOLEAN，不可热更新）。 */
    public static final String MCP_WRITE_TOOLS_ENABLED = "mcp.writeTools.enabled";
    /** Agent 令牌最大有效期（秒，INTEGER，安全项需重启）。 */
    public static final String SECURITY_AGENT_TOKEN_MAX_TTL_SECONDS = "security.agentToken.maxTtlSeconds";
    /** 审计日志保留天数（INTEGER）。 */
    public static final String AUDIT_RETENTION_DAYS = "audit.retentionDays";
    /** 是否启用 Webhook 通知（BOOLEAN）。 */
    public static final String NOTIFICATION_WEBHOOK_ENABLED = "notification.webhook.enabled";
}
