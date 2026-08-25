package com.modelhub.identity.repo;

import com.modelhub.identity.domain.SysUserRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SysUserRoleRepository extends JpaRepository<SysUserRoleEntity, Long> {

    /** 用户活跃角色 code 列表（V16 后为 platformRoles 的唯一真相源）。 */
    @Query("select r.code from SysUserRoleEntity ur, SysRoleEntity r "
            + "where r.id = ur.roleId and ur.userId = :userId and ur.revokedAt is null")
    List<String> findActiveRoleCodes(@Param("userId") Long userId);

    /** 用户活跃权限点 code 列表（角色→角色权限→权限，一次查询推导）。 */
    @Query("select distinct p.code from SysUserRoleEntity ur, SysRolePermissionEntity rp, SysPermissionEntity p "
            + "where ur.userId = :userId and ur.revokedAt is null "
            + "and rp.roleId = ur.roleId and p.id = rp.permissionId")
    List<String> findActivePermissionCodes(@Param("userId") Long userId);

    List<SysUserRoleEntity> findByUserIdAndRevokedAtIsNull(Long userId);

    List<SysUserRoleEntity> findByUserIdInAndRevokedAtIsNull(Collection<Long> userIds);

    List<SysUserRoleEntity> findByRoleIdAndRevokedAtIsNull(Long roleId);

    Optional<SysUserRoleEntity> findByUserIdAndRoleIdAndRevokedAtIsNull(Long userId, Long roleId);

    long countByRoleIdAndRevokedAtIsNull(Long roleId);

    /** 角色删除前清理全部指派（含已吊销历史），避免 role_id 外键阻止删除。 */
    @Modifying
    void deleteByRoleId(Long roleId);

    /** 任意活跃角色指派计数（bootstrap 判空：平台是否已有持久角色，02 §2）。 */
    long countByRevokedAtIsNull();

    /** 排除指定用户后的活跃 platform_admin 指派数（最后管理员守卫）。 */
    @Query("select count(ur) from SysUserRoleEntity ur, SysRoleEntity r "
            + "where r.id = ur.roleId and r.code = 'platform_admin' "
            + "and ur.revokedAt is null and ur.userId <> :excludeUserId")
    long countActivePlatformAdminsExcluding(@Param("excludeUserId") Long excludeUserId);
}
