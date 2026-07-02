/*
 * 功能: authorization 应用层视图与命令对象集合，对外只暴露视图，绝不返回持久化实体。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.authorization.application;

import com.aihub.authorization.domain.PermissionDefinition;
import com.aihub.authorization.domain.ResourceAcl;
import com.aihub.authorization.domain.Role;
import com.aihub.authorization.domain.RoleBinding;
import com.aihub.authorization.domain.RoleType;
import com.aihub.authorization.domain.ScopeType;
import java.time.Instant;
import java.util.List;

/**
 * authorization 应用层 DTO 集合。
 *
 * <p>承载用例输入命令与对外视图；字段名与 OpenAPI 契约 schema 对齐。视图由领域对象转换得到，
 * 不外泄持久化实体。
 */
public final class AuthorizationDtos {

    private AuthorizationDtos() {
    }

    /** 权限定义视图（PermissionView）。 */
    public record PermissionView(String permissionCode, String resource, String action, String i18nKey) {
        public static PermissionView from(PermissionDefinition def) {
            return new PermissionView(def.code(), def.resource(), def.action(),
                    "permission." + def.resource() + "." + def.action());
        }
    }

    /** 角色视图（RoleView）。 */
    public record RoleView(String roleId, String roleCode, String roleName, RoleType roleType,
                           List<String> permissionCodes, long version) {
        public static RoleView from(Role role) {
            return new RoleView(role.roleId(), role.code(), role.name(), role.roleType(),
                    role.permissionCodeList(), Math.max(1, role.rowVersion() + 1));
        }
    }

    /** 角色绑定视图（RoleBindingView）。 */
    public record RoleBindingView(String bindingId, String principalId, String roleId,
                                  ScopeType scopeType, String scopeId, Instant createdAt) {
        public static RoleBindingView from(RoleBinding binding) {
            return new RoleBindingView(binding.bindingId(), binding.principalId(), binding.roleId(),
                    binding.scopeType(), binding.scopeId(), binding.createdAt());
        }
    }

    /** 资源 ACL 视图（ResourceAclView），同一资源+主体的多条权限聚合为一个视图。 */
    public record ResourceAclView(String aclId, String principalId, String resourceType,
                                  String resourceId, List<String> permissionCodes, Instant createdAt) {
        public static ResourceAclView from(ResourceAcl acl) {
            return new ResourceAclView(acl.aclId(), acl.principalId(), acl.resourceType(),
                    acl.resourceId(), acl.permissionList(), acl.createdAt());
        }
    }

    /** 创建角色命令（CreateRoleRequest）。 */
    public record CreateRoleCommand(String roleCode, String roleName, List<String> permissionCodes) {
    }

    /** 更新角色命令（UpdateRoleRequest）。 */
    public record UpdateRoleCommand(String roleName, List<String> permissionCodes, long expectedVersion) {
    }

    /** 创建角色绑定命令（CreateRoleBindingRequest）。 */
    public record CreateRoleBindingCommand(String principalId, String roleId,
                                           ScopeType scopeType, String scopeId) {
    }

    /** 创建资源 ACL 命令（CreateResourceAclRequest）。 */
    public record CreateResourceAclCommand(String principalId, String resourceType,
                                           String resourceId, List<String> permissionCodes) {
    }
}
