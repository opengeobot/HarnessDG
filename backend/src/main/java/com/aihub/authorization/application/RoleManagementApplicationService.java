/*
 * 功能: 角色管理应用服务，编排角色查询、创建、更新与删除用例（含内置角色保护与权限校验）。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.authorization.application;

import com.aihub.authorization.application.AuthorizationDtos.CreateRoleCommand;
import com.aihub.authorization.application.AuthorizationDtos.RoleView;
import com.aihub.authorization.application.AuthorizationDtos.UpdateRoleCommand;
import com.aihub.authorization.domain.PermissionRepository;
import com.aihub.authorization.domain.Role;
import com.aihub.authorization.domain.RoleRepository;
import com.aihub.authorization.domain.ScopeType;
import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 角色管理应用服务。
 *
 * <p>用例编排：列表、创建（权限编码须均已注册）、更新（乐观锁 + 内置角色禁止改名）、删除（内置角色与
 * 仍被绑定引用的角色不可删除）。写操作经 AuditPort 记录审计事件。
 */
@Service
public class RoleManagementApplicationService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final IdGenerator idGenerator;
    private final AuditPort auditPort;
    private final Clock clock;

    public RoleManagementApplicationService(RoleRepository roleRepository,
                                            PermissionRepository permissionRepository,
                                            IdGenerator idGenerator,
                                            AuditPort auditPort,
                                            Clock clock) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.idGenerator = idGenerator;
        this.auditPort = auditPort;
        this.clock = clock;
    }

    /**
     * 查询全部角色。
     */
    @Transactional(readOnly = true)
    public List<RoleView> listRoles() {
        return roleRepository.findAll().stream().map(RoleView::from).toList();
    }

    /**
     * 创建自定义角色。
     */
    @Transactional
    public RoleView createRole(CreateRoleCommand command) {
        if (command.roleCode() == null || command.roleCode().isBlank()) {
            throw new ValidationException("roleCode is required");
        }
        if (command.roleName() == null || command.roleName().isBlank()) {
            throw new ValidationException("roleName is required");
        }
        String code = command.roleCode().trim();
        if (roleRepository.existsByCode(code)) {
            throw new ConflictException(ErrorCode.ROLE_ALREADY_EXISTS,
                    "role code already exists", Map.of("roleCode", code));
        }
        validatePermissions(command.permissionCodes());

        Instant now = clock.instant();
        Role role = new Role(idGenerator.generate(IdPrefix.ROLE), code, command.roleName().trim(), null,
                ScopeType.PLATFORM, null, false, "ACTIVE",
                Set.copyOf(command.permissionCodes()), now, now, 0L);
        roleRepository.create(role);
        auditPort.record("ROLE_CREATED", actorId(), role.roleId(), Map.of("roleCode", code));
        return RoleView.from(role);
    }

    /**
     * 更新自定义角色名称与权限集合。
     */
    @Transactional
    public RoleView updateRole(String roleId, UpdateRoleCommand command) {
        Role role = requireRole(roleId);
        if (role.builtin()) {
            throw new ConflictException(ErrorCode.ROLE_BUILTIN_IMMUTABLE,
                    "builtin role is immutable", Map.of("roleId", roleId));
        }
        // 乐观锁：契约 version = rowVersion + 1（见 RoleView.from）。
        long expectedRowVersion = command.expectedVersion() - 1;
        if (expectedRowVersion != role.rowVersion()) {
            throw new ConflictException(ErrorCode.CONCURRENT_MODIFICATION,
                    "role concurrently modified", Map.of("roleId", roleId));
        }
        if (command.permissionCodes() != null) {
            validatePermissions(command.permissionCodes());
        }
        role.update(command.roleName(),
                command.permissionCodes() == null ? null : Set.copyOf(command.permissionCodes()),
                clock.instant());
        if (!roleRepository.update(role)) {
            throw new ConflictException(ErrorCode.CONCURRENT_MODIFICATION,
                    "role concurrently modified", Map.of("roleId", roleId));
        }
        auditPort.record("ROLE_UPDATED", actorId(), roleId, Map.of());
        return RoleView.from(role);
    }

    /**
     * 删除无有效绑定的自定义角色。
     */
    @Transactional
    public void deleteRole(String roleId) {
        Role role = requireRole(roleId);
        if (role.builtin()) {
            throw new ConflictException(ErrorCode.ROLE_BUILTIN_IMMUTABLE,
                    "builtin role cannot be deleted", Map.of("roleId", roleId));
        }
        if (roleRepository.countBindingsByRoleId(roleId) > 0) {
            throw new ConflictException(ErrorCode.ROLE_IN_USE,
                    "role is still bound to principals", Map.of("roleId", roleId));
        }
        roleRepository.deleteByRoleId(roleId);
        auditPort.record("ROLE_DELETED", actorId(), roleId, Map.of());
    }

    private void validatePermissions(List<String> permissionCodes) {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return;
        }
        List<String> unknown = permissionRepository.findUnknownCodes(permissionCodes);
        if (!unknown.isEmpty()) {
            throw new ValidationException(ErrorCode.PERMISSION_UNKNOWN,
                    "unknown permission codes", Map.of("unknown", unknown));
        }
    }

    private Role requireRole(String roleId) {
        return roleRepository.findByRoleId(roleId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ROLE_NOT_FOUND, "role not found", Map.of()));
    }

    private String actorId() {
        return PrincipalContextHolder.current().map(PrincipalContext::principalId).orElse(null);
    }
}
