/*
 * 功能: 角色领域聚合，承载角色身份、作用域、内置标记与权限编码集合。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.authorization.domain;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 角色聚合。
 *
 * <p>承载角色身份与其权限编码集合。内置角色（{@code builtin=true}）不可改名/删除，由领域规则保证。
 * 集合字段在构造时复制为不可变视图。
 */
public final class Role {

    private final String roleId;
    private final String code;
    private String name;
    private final String description;
    private final ScopeType scopeType;
    private final String scopeId;
    private final boolean builtin;
    private final String status;
    private Set<String> permissionCodes;
    private final Instant createdAt;
    private Instant updatedAt;
    private long rowVersion;

    public Role(String roleId, String code, String name, String description,
                ScopeType scopeType, String scopeId, boolean builtin, String status,
                Set<String> permissionCodes, Instant createdAt, Instant updatedAt, long rowVersion) {
        this.roleId = roleId;
        this.code = code;
        this.name = name;
        this.description = description;
        this.scopeType = scopeType;
        this.scopeId = scopeId;
        this.builtin = builtin;
        this.status = status;
        this.permissionCodes = permissionCodes == null ? Set.of() : Set.copyOf(permissionCodes);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.rowVersion = rowVersion;
    }

    /**
     * 更新角色名称与权限集合。内置角色禁止改名（由应用层在调用前拦截，领域再次兜底）。
     *
     * @param newName            新名称（为空表示不改名）
     * @param newPermissionCodes 新权限集合（为空表示不改权限）
     * @param now                更新时间
     */
    public void update(String newName, Set<String> newPermissionCodes, Instant now) {
        if (newName != null && !newName.isBlank()) {
            this.name = newName;
        }
        if (newPermissionCodes != null) {
            this.permissionCodes = Set.copyOf(newPermissionCodes);
        }
        this.updatedAt = now;
        this.rowVersion = this.rowVersion + 1;
    }

    public String roleId() {
        return roleId;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public ScopeType scopeType() {
        return scopeType;
    }

    public String scopeId() {
        return scopeId;
    }

    public boolean builtin() {
        return builtin;
    }

    public String status() {
        return status;
    }

    public Set<String> permissionCodes() {
        return permissionCodes;
    }

    /**
     * @return 角色类型：内置为 SYSTEM，否则 CUSTOM
     */
    public RoleType roleType() {
        return builtin ? RoleType.SYSTEM : RoleType.CUSTOM;
    }

    public List<String> permissionCodeList() {
        return List.copyOf(permissionCodes);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long rowVersion() {
        return rowVersion;
    }
}
