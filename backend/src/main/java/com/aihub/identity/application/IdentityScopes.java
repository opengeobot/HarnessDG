/*
 * 功能: identity 模块粗粒度 Scope 常量（P0-B 简化授权来源，Task 5 角色体系落地后由授权服务接管）。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.application;

import java.util.Set;

/**
 * 粗粒度 Scope 常量。
 *
 * <p>P0-B 阶段管理端点直接基于这些 Scope 做占位授权校验；Task 5 AuthorizationService 落地后，
 * 这些字符串将由统一权限模型（角色/权限/绑定）映射，管理端点改用 AuthorizationService。
 */
public final class IdentityScopes {

    /** 读取用户/主体。 */
    public static final String USER_READ = "user:read";

    /** 管理用户（创建/更新/启停/重置口令）。 */
    public static final String USER_MANAGE = "user:manage";

    /** 读取授权相关（Agent 列表）。 */
    public static final String AUTHORIZATION_READ = "authorization:read";

    /** 注册 Agent。 */
    public static final String AGENT_REGISTER = "agent:register";

    /** 授权 Agent（启停/Tool 白名单）。 */
    public static final String AGENT_AUTHORIZE = "agent:authorize";

    /** 平台首个管理员的基础管理 Scope 集合。 */
    public static final Set<String> ADMIN_SCOPES = Set.of(
            USER_READ, USER_MANAGE, AUTHORIZATION_READ, AGENT_REGISTER, AGENT_AUTHORIZE);

    private IdentityScopes() {
    }
}
