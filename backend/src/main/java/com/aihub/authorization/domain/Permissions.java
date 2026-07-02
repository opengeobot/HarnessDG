/*
 * 功能: 统一权限编码清单常量（resource:action），与 V4 预置 iam_permission 保持一致。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.authorization.domain;

import java.util.Set;

/**
 * 平台统一权限编码清单。
 *
 * <p>权限以 {@code resource:action} 编码，是 RBAC 的最小授权单元；常量值必须与 V4 迁移预置的
 * {@code iam_permission.code} 一致。其它模块的适配层引用本清单做授权校验，避免散落硬编码字符串。
 */
public final class Permissions {

    /** 管理组织。 */
    public static final String ORGANIZATION_MANAGE = "organization:manage";
    /** 查看项目。 */
    public static final String PROJECT_VIEW = "project:view";
    /** 管理项目。 */
    public static final String PROJECT_MANAGE = "project:manage";
    /** 查看用户/主体。 */
    public static final String USER_READ = "user:read";
    /** 管理用户。 */
    public static final String USER_MANAGE = "user:manage";
    /** 查看授权（角色/绑定/ACL）。 */
    public static final String AUTHORIZATION_READ = "authorization:read";
    /** 管理授权（角色/绑定/ACL）。 */
    public static final String AUTHORIZATION_MANAGE = "authorization:manage";
    /** 查看字典。 */
    public static final String DICTIONARY_READ = "dictionary:read";
    /** 管理字典。 */
    public static final String DICTIONARY_MANAGE = "dictionary:manage";
    /** 查看标签。 */
    public static final String TAG_READ = "tag:read";
    /** 管理标签。 */
    public static final String TAG_MANAGE = "tag:manage";
    /** 创建资产。 */
    public static final String ASSET_CREATE = "asset:create";
    /** 查看资产。 */
    public static final String ASSET_READ = "asset:read";
    /** 管理资产。 */
    public static final String ASSET_MANAGE = "asset:manage";
    /** 更新资产。 */
    public static final String ASSET_UPDATE = "asset:update";
    /** 上传资产内容。 */
    public static final String ASSET_UPLOAD = "asset:upload";
    /** 下载资产内容。 */
    public static final String ASSET_DOWNLOAD = "asset:download";
    /** 提交资产版本。 */
    public static final String ASSET_SUBMIT = "asset:submit";
    /** 评审资产版本。 */
    public static final String ASSET_REVIEW = "asset:review";
    /** 发布资产版本（高风险）。 */
    public static final String ASSET_PUBLISH = "asset:publish";
    /** 弃用资产。 */
    public static final String ASSET_DEPRECATE = "asset:deprecate";
    /** 删除资产（高风险）。 */
    public static final String ASSET_DELETE = "asset:delete";
    /** 注册 Agent。 */
    public static final String AGENT_REGISTER = "agent:register";
    /** 授权 Agent。 */
    public static final String AGENT_AUTHORIZE = "agent:authorize";
    /** 签发令牌/凭据。 */
    public static final String TOKEN_CREATE = "token:create";
    /** 查看审计。 */
    public static final String AUDIT_READ = "audit:read";
    /** 查看任务。 */
    public static final String JOB_READ = "job:read";
    /** 管理任务。 */
    public static final String JOB_MANAGE = "job:manage";
    /** 查看通知。 */
    public static final String NOTIFICATION_READ = "notification:read";
    /** 系统配置（高风险）。 */
    public static final String SYSTEM_CONFIGURE = "system:configure";
    /** 系统观测。 */
    public static final String SYSTEM_OBSERVE = "system:observe";
    /** 调用 MCP 工具。 */
    public static final String MCP_INVOKE = "mcp:invoke";

    /**
     * 高风险动作集合：默认不授予 Agent 主体，即使其 Scope/角色包含也拒绝。
     */
    public static final Set<String> HIGH_RISK_ACTIONS = Set.of(
            ASSET_PUBLISH, ASSET_DELETE, SYSTEM_CONFIGURE);

    private Permissions() {
    }
}
