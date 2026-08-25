package com.modelhub.identity.service;

import com.modelhub.identity.domain.SysPermissionEntity;
import com.modelhub.identity.domain.SysRoleEntity;
import com.modelhub.identity.domain.SysRolePermissionEntity;
import com.modelhub.identity.domain.SysUserRoleEntity;
import com.modelhub.identity.domain.UserEntity;
import com.modelhub.identity.repo.SysPermissionRepository;
import com.modelhub.identity.repo.SysRolePermissionRepository;
import com.modelhub.identity.repo.SysRoleRepository;
import com.modelhub.identity.repo.SysUserRoleRepository;
import com.modelhub.identity.repo.UserRepository;
import com.modelhub.identity.security.CurrentPrincipal;
import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.error.ErrorCode;
import com.modelhub.shared.id.PublicIds;
import com.modelhub.shared.web.ETags;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * RBAC 管理面服务（管理后台计划 §三）：角色/权限/指派的管理操作。
 * 写操作仅 platform_admin；全部落审计；角色实时解析，吊销即时生效。
 */
@Service
public class RbacAdminService {

    private static final Pattern ROLE_CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{2,63}$");

    /** 契约 Role schema。 */
    public record RoleView(UUID id, String code, boolean builtIn, String description,
                           List<String> permissions, long assigneeCount, long version) {}

    /** 契约 Permission schema。 */
    public record PermissionView(String code, String module, String description) {}

    private final SysRoleRepository roles;
    private final SysPermissionRepository permissions;
    private final SysRolePermissionRepository rolePermissions;
    private final SysUserRoleRepository userRoles;
    private final UserRepository users;
    private final AuditService auditService;

    public RbacAdminService(SysRoleRepository roles, SysPermissionRepository permissions,
                            SysRolePermissionRepository rolePermissions, SysUserRoleRepository userRoles,
                            UserRepository users, AuditService auditService) {
        this.roles = roles;
        this.permissions = permissions;
        this.rolePermissions = rolePermissions;
        this.userRoles = userRoles;
        this.users = users;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<RoleView> listRoles(CurrentPrincipal actor) {
        requireAdmin(actor);
        List<SysRoleEntity> all = roles.findAllByOrderById();
        Map<Long, List<String>> permsByRole = permissionCodesByRole(
                all.stream().map(SysRoleEntity::getId).toList());
        return all.stream()
                .map(r -> new RoleView(r.getPublicId(), r.getCode(), r.isBuiltIn(), r.getDescription(),
                        permsByRole.getOrDefault(r.getId(), List.of()),
                        userRoles.countByRoleIdAndRevokedAtIsNull(r.getId()), r.getVersion()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionView> listPermissions(CurrentPrincipal actor) {
        requireAdmin(actor);
        return permissions.findAllByOrderById().stream()
                .map(p -> new PermissionView(p.getCode(), p.getModule(), p.getDescription()))
                .toList();
    }

    @Transactional
    public RoleView createRole(CurrentPrincipal actor, String code, String description,
                               List<String> permissionCodes) {
        requireAdmin(actor);
        if (code == null || !ROLE_CODE_PATTERN.matcher(code).matches()) {
            throw ApiException.badRequest("角色 code 必须为 3-64 位小写字母/数字/下划线，且以字母开头",
                    List.of(new ApiException.Detail("code", "invalid_format")));
        }
        if (roles.existsByCode(code)) {
            throw new ApiException(ErrorCode.CONFLICT, "角色 code 已存在");
        }
        List<SysPermissionEntity> perms = resolvePermissions(permissionCodes);
        SysRoleEntity role = new SysRoleEntity();
        role.setPublicId(PublicIds.next());
        role.setCode(code);
        role.setDescription(description);
        roles.save(role);
        for (SysPermissionEntity p : perms) {
            rolePermissions.save(new SysRolePermissionEntity(role.getId(), p.getId()));
        }
        auditService.appendSimple(actor.username(), "role.create", "role:" + code, "success");
        return new RoleView(role.getPublicId(), role.getCode(), role.isBuiltIn(), role.getDescription(),
                perms.stream().map(SysPermissionEntity::getCode).toList(), 0L, role.getVersion());
    }

    /** 更新角色：built_in 角色仅可改描述，禁止变更权限（409）。If-Match 条件更新。 */
    @Transactional
    public RoleView updateRole(CurrentPrincipal actor, UUID rolePublicId, String description,
                               List<String> permissionCodes, String ifMatch) {
        requireAdmin(actor);
        SysRoleEntity role = roles.findByPublicId(rolePublicId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "角色不存在"));
        ETags.requireMatch(ifMatch, ETags.ofVersion(role.getVersion()), "角色");
        if (permissionCodes != null) {
            if (role.isBuiltIn()) {
                throw new ApiException(ErrorCode.CONFLICT, "内置角色的权限不可修改");
            }
            List<SysPermissionEntity> perms = resolvePermissions(permissionCodes);
            rolePermissions.deleteByRoleId(role.getId());
            for (SysPermissionEntity p : perms) {
                rolePermissions.save(new SysRolePermissionEntity(role.getId(), p.getId()));
            }
        }
        if (description != null) {
            role.setDescription(description);
        }
        role.setVersion(role.getVersion() + 1);
        role.setUpdatedAt(OffsetDateTime.now());
        roles.save(role);
        auditService.appendSimple(actor.username(), "role.update", "role:" + role.getCode(), "success");
        return toView(role);
    }

    @Transactional
    public void deleteRole(CurrentPrincipal actor, UUID rolePublicId) {
        requireAdmin(actor);
        SysRoleEntity role = roles.findByPublicId(rolePublicId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "角色不存在"));
        if (role.isBuiltIn()) {
            throw new ApiException(ErrorCode.CONFLICT, "内置角色不可删除");
        }
        if (userRoles.countByRoleIdAndRevokedAtIsNull(role.getId()) > 0) {
            throw new ApiException(ErrorCode.CONFLICT, "角色仍有活跃指派，不可删除");
        }
        rolePermissions.deleteByRoleId(role.getId());
        // 已吊销的指派历史仍引用 role_id，先清理避免外键违反（审计已由 role.revoke 记录）
        userRoles.deleteByRoleId(role.getId());
        roles.delete(role);
        auditService.appendSimple(actor.username(), "role.delete", "role:" + role.getCode(), "success");
    }

    /** 指派角色：禁止自我指派；重复活跃指派 409。 */
    @Transactional
    public List<String> assignRole(CurrentPrincipal actor, UUID userPublicId, String roleCode) {
        requireAdmin(actor);
        UserEntity target = users.findByPublicId(userPublicId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));
        SysRoleEntity role = roles.findByCode(roleCode)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "角色不存在"));
        if (!"active".equals(role.getStatus())) {
            throw new ApiException(ErrorCode.CONFLICT, "角色已停用，不可指派");
        }
        if (target.getId().equals(actor.userId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "禁止给自己指派角色");
        }
        if (userRoles.findByUserIdAndRoleIdAndRevokedAtIsNull(target.getId(), role.getId()).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT, "该用户已持有此角色");
        }
        SysUserRoleEntity assignment = new SysUserRoleEntity();
        assignment.setUserId(target.getId());
        assignment.setRoleId(role.getId());
        assignment.setCreatedBy(actor.userId());
        userRoles.save(assignment);
        auditService.appendSimple(actor.username(), "role.assign",
                "user:" + userPublicId + ":role:" + roleCode, "success");
        return userRoles.findActiveRoleCodes(target.getId());
    }

    /** 吊销角色：禁止自我吊销；不得吊销最后一个活跃 platform_admin（409）。 */
    @Transactional
    public List<String> revokeRole(CurrentPrincipal actor, UUID userPublicId, String roleCode) {
        requireAdmin(actor);
        UserEntity target = users.findByPublicId(userPublicId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));
        SysRoleEntity role = roles.findByCode(roleCode)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "角色不存在"));
        boolean lastPlatformAdmin = "platform_admin".equals(role.getCode())
                && userRoles.countActivePlatformAdminsExcluding(target.getId()) == 0;
        // 最后管理员守卫优先于自我吊销守卫（与 disable 守卫语义一致，409 > 403）
        if (lastPlatformAdmin) {
            throw new ApiException(ErrorCode.CONFLICT, "不可吊销最后一个活跃 platform_admin 的角色");
        }
        if (target.getId().equals(actor.userId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "禁止吊销自己的角色");
        }
        SysUserRoleEntity assignment = userRoles
                .findByUserIdAndRoleIdAndRevokedAtIsNull(target.getId(), role.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "该用户未持有此角色"));
        assignment.setRevokedAt(OffsetDateTime.now());
        assignment.setRevokedBy(actor.userId());
        userRoles.save(assignment);
        auditService.appendSimple(actor.username(), "role.revoke",
                "user:" + userPublicId + ":role:" + roleCode, "success");
        return userRoles.findActiveRoleCodes(target.getId());
    }

    /** 指定用户的活跃角色 code 列表（管理端用户详情）。 */
    @Transactional(readOnly = true)
    public List<String> activeRoleCodes(CurrentPrincipal actor, UUID userPublicId) {
        requireAdmin(actor);
        UserEntity target = users.findByPublicId(userPublicId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));
        return userRoles.findActiveRoleCodes(target.getId());
    }

    // ---------- 内部 ----------

    private RoleView toView(SysRoleEntity role) {
        Map<Long, List<String>> permsByRole = permissionCodesByRole(List.of(role.getId()));
        return new RoleView(role.getPublicId(), role.getCode(), role.isBuiltIn(), role.getDescription(),
                permsByRole.getOrDefault(role.getId(), List.of()),
                userRoles.countByRoleIdAndRevokedAtIsNull(role.getId()), role.getVersion());
    }

    private Map<Long, List<String>> permissionCodesByRole(List<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> permCodeById = permissions.findAll().stream()
                .collect(Collectors.toMap(SysPermissionEntity::getId, SysPermissionEntity::getCode));
        return rolePermissions.findByRoleIdIn(roleIds).stream()
                .collect(Collectors.groupingBy(SysRolePermissionEntity::getRoleId,
                        Collectors.mapping(rp -> permCodeById.getOrDefault(rp.getPermissionId(), ""),
                                Collectors.toList())));
    }

    private List<SysPermissionEntity> resolvePermissions(List<String> codes) {
        List<String> wanted = codes == null ? List.of() : codes;
        Set<String> unique = new HashSet<>(wanted);
        List<SysPermissionEntity> found = permissions.findByCodeIn(unique);
        if (found.size() != unique.size()) {
            Set<String> known = found.stream().map(SysPermissionEntity::getCode).collect(Collectors.toSet());
            List<String> unknown = unique.stream().filter(c -> !known.contains(c)).toList();
            throw ApiException.badRequest("权限码未注册: " + unknown,
                    List.of(new ApiException.Detail("permissions", "unknown_code")));
        }
        return found;
    }

    private static void requireAdmin(CurrentPrincipal actor) {
        if (actor == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "未认证或凭证已过期");
        }
        if (!actor.isPlatformAdmin()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "仅 platform_admin 可执行该操作");
        }
    }
}
