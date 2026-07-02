/*
 * 功能: authorization API 请求体集合，字段名与 OpenAPI 契约 schema 对齐。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.authorization.api;

import com.aihub.authorization.domain.ScopeType;
import java.util.List;

/**
 * authorization API 请求体集合。
 *
 * <p>仅承载入参，不暴露领域对象；字段名与 OpenAPI 契约对齐。
 */
public final class AuthorizationRequests {

    private AuthorizationRequests() {
    }

    /** 创建角色请求（CreateRoleRequest）。 */
    public record CreateRoleRequest(String roleCode, String roleName, List<String> permissionCodes) {
    }

    /** 更新角色请求（UpdateRoleRequest）。 */
    public record UpdateRoleRequest(String roleName, List<String> permissionCodes, Long expectedVersion) {
    }

    /** 创建角色绑定请求（CreateRoleBindingRequest）。 */
    public record CreateRoleBindingRequest(String principalId, String roleId,
                                           ScopeType scopeType, String scopeId) {
    }

    /** 创建资源 ACL 请求（CreateResourceAclRequest）。 */
    public record CreateResourceAclRequest(String principalId, String resourceType,
                                           String resourceId, List<String> permissionCodes) {
    }
}
