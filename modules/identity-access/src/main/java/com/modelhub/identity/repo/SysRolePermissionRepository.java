package com.modelhub.identity.repo;

import com.modelhub.identity.domain.SysRolePermissionEntity;
import com.modelhub.identity.domain.SysRolePermissionEntity.RolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SysRolePermissionRepository
        extends JpaRepository<SysRolePermissionEntity, RolePermissionId> {

    List<SysRolePermissionEntity> findByRoleId(Long roleId);

    List<SysRolePermissionEntity> findByRoleIdIn(Collection<Long> roleIds);

    @Modifying
    @Query("delete from SysRolePermissionEntity rp where rp.roleId = :roleId")
    int deleteByRoleId(@Param("roleId") Long roleId);
}
