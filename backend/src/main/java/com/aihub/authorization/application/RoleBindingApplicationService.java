/*
 * 功能: 角色绑定应用服务，编排主体作用域角色绑定的查询、创建与删除，并补齐平台首个管理员绑定。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.authorization.application;

import com.aihub.authorization.application.AuthorizationDtos.CreateRoleBindingCommand;
import com.aihub.authorization.application.AuthorizationDtos.RoleBindingView;
import com.aihub.authorization.domain.Role;
import com.aihub.authorization.domain.RoleBinding;
import com.aihub.authorization.domain.RoleBindingRepository;
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
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 角色绑定应用服务。
 *
 * <p>用例编排：列表、创建（作用域一致性校验 + 角色存在校验 + 唯一性）、删除。另提供
 * {@link #ensurePlatformAdmin} 供运行时引导器为首个管理员补齐 ADMIN 平台绑定（不破坏模块边界）。
 */
@Service
public class RoleBindingApplicationService {

    /** 内置平台管理员角色业务键（与 V4 预置一致）。 */
    static final String PLATFORM_ADMIN_ROLE_ID = "rol_admin";

    private final RoleBindingRepository roleBindingRepository;
    private final RoleRepository roleRepository;
    private final IdGenerator idGenerator;
    private final AuditPort auditPort;
    private final Clock clock;

    public RoleBindingApplicationService(RoleBindingRepository roleBindingRepository,
                                         RoleRepository roleRepository,
                                         IdGenerator idGenerator,
                                         AuditPort auditPort,
                                         Clock clock) {
        this.roleBindingRepository = roleBindingRepository;
        this.roleRepository = roleRepository;
        this.idGenerator = idGenerator;
        this.auditPort = auditPort;
        this.clock = clock;
    }

    /**
     * 查询全部角色绑定。
     */
    @Transactional(readOnly = true)
    public List<RoleBindingView> listBindings() {
        return roleBindingRepository.findAll().stream().map(RoleBindingView::from).toList();
    }

    /**
     * 创建角色绑定。
     */
    @Transactional
    public RoleBindingView createBinding(CreateRoleBindingCommand command) {
        if (command.principalId() == null || command.principalId().isBlank()) {
            throw new ValidationException("principalId is required");
        }
        if (command.roleId() == null || command.roleId().isBlank()) {
            throw new ValidationException("roleId is required");
        }
        ScopeType scopeType = command.scopeType();
        if (scopeType == null) {
            throw new ValidationException("scopeType is required");
        }
        // 角色绑定只允许平台/组织/项目作用域；ASSET 作用域由资源 ACL 承载。
        if (scopeType == ScopeType.ASSET) {
            throw new ValidationException("ASSET scope is not allowed for role binding; use resource ACL instead");
        }
        // 平台作用域 scopeId 必须为空，其他作用域必须提供。
        boolean platform = scopeType == ScopeType.PLATFORM;
        boolean hasScopeId = command.scopeId() != null && !command.scopeId().isBlank();
        if (platform && hasScopeId) {
            throw new ValidationException("PLATFORM scope must not carry scopeId");
        }
        if (!platform && !hasScopeId) {
            throw new ValidationException("scopeId is required for non-PLATFORM scope");
        }
        Role role = roleRepository.findByRoleId(command.roleId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.ROLE_NOT_FOUND, "role not found", Map.of()));
        String scopeId = platform ? null : command.scopeId().trim();
        if (roleBindingRepository.exists(command.principalId(), role.roleId(), scopeType, scopeId)) {
            throw new ConflictException(ErrorCode.ROLE_BINDING_ALREADY_EXISTS,
                    "role binding already exists", Map.of());
        }
        RoleBinding binding = new RoleBinding(idGenerator.generate(IdPrefix.ROLE_BINDING),
                command.principalId(), role.roleId(), scopeType, scopeId, actorId(), clock.instant());
        roleBindingRepository.create(binding);
        auditPort.record("ROLE_BINDING_CREATED", actorId(), binding.bindingId(),
                Map.of("principalId", command.principalId(), "roleId", role.roleId()));
        return RoleBindingView.from(binding);
    }

    /**
     * 删除角色绑定。
     */
    @Transactional
    public void deleteBinding(String bindingId) {
        roleBindingRepository.findByBindingId(bindingId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ROLE_BINDING_NOT_FOUND,
                        "role binding not found", Map.of()));
        roleBindingRepository.deleteByBindingId(bindingId);
        auditPort.record("ROLE_BINDING_DELETED", actorId(), bindingId, Map.of());
    }

    /**
     * 确保给定主体拥有平台 ADMIN 角色绑定（幂等）。供运行时引导器调用以补齐首个管理员绑定。
     *
     * @param principalId 主体 ID
     */
    @Transactional
    public void ensurePlatformAdmin(String principalId) {
        if (principalId == null || principalId.isBlank()) {
            return;
        }
        if (roleBindingRepository.exists(principalId, PLATFORM_ADMIN_ROLE_ID, ScopeType.PLATFORM, null)) {
            return;
        }
        RoleBinding binding = new RoleBinding(idGenerator.generate(IdPrefix.ROLE_BINDING),
                principalId, PLATFORM_ADMIN_ROLE_ID, ScopeType.PLATFORM, null, "system", clock.instant());
        roleBindingRepository.create(binding);
        auditPort.record("ROLE_BINDING_CREATED", "system", binding.bindingId(),
                Map.of("principalId", principalId, "roleId", PLATFORM_ADMIN_ROLE_ID, "reason", "bootstrap-admin"));
    }

    private String actorId() {
        return PrincipalContextHolder.current().map(PrincipalContext::principalId).orElse(null);
    }
}
