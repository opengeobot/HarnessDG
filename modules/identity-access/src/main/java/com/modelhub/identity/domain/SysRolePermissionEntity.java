package com.modelhub.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/** RBAC 角色-权限关联（V16）：复合主键 (role_id, permission_id)。 */
@Entity
@Table(name = "sys_role_permission")
@IdClass(SysRolePermissionEntity.RolePermissionId.class)
public class SysRolePermissionEntity {

    @Id
    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Id
    @Column(name = "permission_id", nullable = false)
    private Long permissionId;

    public SysRolePermissionEntity() {}

    public SysRolePermissionEntity(Long roleId, Long permissionId) {
        this.roleId = roleId;
        this.permissionId = permissionId;
    }

    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
    public Long getPermissionId() { return permissionId; }
    public void setPermissionId(Long permissionId) { this.permissionId = permissionId; }

    /** 复合主键。 */
    public static class RolePermissionId implements Serializable {
        private Long roleId;
        private Long permissionId;

        public RolePermissionId() {}

        public RolePermissionId(Long roleId, Long permissionId) {
            this.roleId = roleId;
            this.permissionId = permissionId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RolePermissionId that)) return false;
            return Objects.equals(roleId, that.roleId) && Objects.equals(permissionId, that.permissionId);
        }

        @Override
        public int hashCode() { return Objects.hash(roleId, permissionId); }
    }
}
