/*
 * 功能: 访问主体类型枚举，统一抽象所有访问者身份。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.shared.identity;

/**
 * 访问主体类型。
 *
 * <p>所有访问者统一抽象为 Principal，{@code ADMIN} 是角色而非类型。
 */
public enum PrincipalType {

    /** 企业普通用户与管理员（OIDC/LDAP）。 */
    USER,

    /** OpenClaw、QwenPaw、自研 Agent（OAuth2/Agent Token）。 */
    AGENT,

    /** 后端内部服务或外部业务系统（Client Credentials）。 */
    SERVICE,

    /** SDK、CI/CD、批处理客户端（OAuth2/PAT）。 */
    API_CLIENT,

    /** 平台受控后台 Worker（工作负载身份）。 */
    WORKER
}
