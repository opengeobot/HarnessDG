/*
 * 功能: 统一请求主体上下文，承载身份、作用域、敏感等级与追踪信息。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.shared.identity;

import java.util.List;
import java.util.Set;

/**
 * 统一请求主体上下文。
 *
 * <p>由 Controller、MCP Tool、Worker 入口统一建立，业务代码只读取不解析底层凭据。
 * 集合字段在构造时被复制为不可变视图，保证线程内只读语义。
 *
 * @param principalId         主体 ID（统一 {@code prn_} 前缀；{@code usr_}/{@code agt_} 仅用于各自管理域的内部标识）
 * @param principalType       主体类型
 * @param subject             外部身份主体标识（如 OIDC subject）
 * @param organizationId      当前组织 ID
 * @param projectIds          可访问项目 ID 列表
 * @param roles               角色集合
 * @param scopes              OAuth Scope 集合
 * @param maxSensitivityLevel 可访问的最高敏感等级
 * @param locale              语言偏好
 * @param requestId           请求 ID
 * @param traceId             分布式追踪 ID
 */
public record PrincipalContext(String principalId,
                               PrincipalType principalType,
                               String subject,
                               String organizationId,
                               List<String> projectIds,
                               Set<String> roles,
                               Set<String> scopes,
                               int maxSensitivityLevel,
                               String locale,
                               String requestId,
                               String traceId) {

    /**
     * 紧凑构造器：对集合做不可变拷贝，避免外部修改污染上下文。
     */
    public PrincipalContext {
        projectIds = projectIds == null ? List.of() : List.copyOf(projectIds);
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }
}
