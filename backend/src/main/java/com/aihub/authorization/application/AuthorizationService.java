/*
 * 功能: 统一授权服务，供 REST/MCP/Worker 复用的默认拒绝授权判定（Scope 粗粒度 + RBAC + 资源 ACL）。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.authorization.application;

import com.aihub.authorization.domain.AccessScope;
import com.aihub.authorization.domain.AgentToolRepository;
import com.aihub.authorization.domain.Permissions;
import com.aihub.authorization.domain.ResourceAclRepository;
import com.aihub.authorization.domain.RoleBindingRepository;
import com.aihub.organization.application.OrganizationMembershipPort;
import com.aihub.shared.error.AuthorizationException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.aihub.shared.identity.PrincipalType;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 统一授权服务。
 *
 * <p><b>默认拒绝（fail-closed）：</b>无匹配授权一律拒绝；缺失主体也拒绝。判定组合：
 * <ol>
 *   <li><b>JWT Scope（粗粒度）：</b>登录/凭据交换签发的 access Token 携带粗粒度 Scope。P0-B 合并规则：
 *       若主体 Scope 直接包含所需权限编码，视为已授予（满足现有基于 Scope 的链路与测试）。</li>
 *   <li><b>RBAC（细粒度）：</b>解析主体 role_binding → role → permission，命中所需权限编码则授予。</li>
 *   <li><b>资源 ACL：</b>对带资源引用的判定，主体在该资源上的显式 ACL 授予亦可命中。</li>
 * </ol>
 *
 * <p><b>Agent 高风险动作：</b>{@code asset:publish/asset:delete/system:configure} 对
 * {@link PrincipalType#AGENT} 一律拒绝，即使其 Scope/角色包含。
 *
 * <p><b>防资源枚举：</b>{@link #authorizeResourceOrNotFound} 在"资源不存在"与"无权访问私有资源"时
 * 返回相同的 {@link NotFoundException} 语义。
 */
@Service
public class AuthorizationService {

    private final RoleBindingRepository roleBindingRepository;
    private final ResourceAclRepository resourceAclRepository;
    private final AgentToolRepository agentToolRepository;
    private final ObjectProvider<OrganizationMembershipPort> membershipPortProvider;

    public AuthorizationService(RoleBindingRepository roleBindingRepository,
                                ResourceAclRepository resourceAclRepository,
                                AgentToolRepository agentToolRepository) {
        this(roleBindingRepository, resourceAclRepository, agentToolRepository, null);
    }

    @Autowired
    public AuthorizationService(RoleBindingRepository roleBindingRepository,
                                ResourceAclRepository resourceAclRepository,
                                AgentToolRepository agentToolRepository,
                                ObjectProvider<OrganizationMembershipPort> membershipPortProvider) {
        this.roleBindingRepository = roleBindingRepository;
        this.resourceAclRepository = resourceAclRepository;
        this.agentToolRepository = agentToolRepository;
        this.membershipPortProvider = membershipPortProvider;
    }

    /**
     * 要求当前主体（来自 ThreadLocal 上下文）具备指定权限，否则抛出 403。
     *
     * @param permissionCode 所需权限编码（resource:action）
     */
    public void requirePermission(String permissionCode) {
        requirePermission(currentContext(), permissionCode);
    }

    /**
     * 要求给定主体具备指定权限，否则抛出 403。
     *
     * @param context        主体上下文
     * @param permissionCode 所需权限编码
     */
    public void requirePermission(PrincipalContext context, String permissionCode) {
        if (!isPermitted(context, permissionCode)) {
            throw new AuthorizationException(ErrorCode.AUTH_PERMISSION_DENIED,
                    "missing required permission", Map.of("requiredPermission", permissionCode));
        }
    }

    /**
     * 判断当前主体是否具备指定权限（默认拒绝）。
     */
    public boolean isPermitted(String permissionCode) {
        return isPermitted(PrincipalContextHolder.current().orElse(null), permissionCode);
    }

    /**
     * 判断给定主体是否具备指定权限（默认拒绝）。
     *
     * @param context        主体上下文（可空 → 拒绝）
     * @param permissionCode 所需权限编码
     * @return 是否授予
     */
    public boolean isPermitted(PrincipalContext context, String permissionCode) {
        if (context == null || context.principalId() == null || permissionCode == null) {
            return false;
        }
        // 高风险动作对 Agent 一律拒绝。
        if (isAgent(context) && Permissions.HIGH_RISK_ACTIONS.contains(permissionCode)) {
            return false;
        }
        // 粗粒度 Scope 直接命中。
        if (context.scopes() != null && context.scopes().contains(permissionCode)) {
            return true;
        }
        // 细粒度 RBAC 解析。
        Set<String> granted = roleBindingRepository.resolvePermissionCodes(context.principalId());
        return granted.contains(permissionCode);
    }

    /**
     * 资源级授权：先看主体是否具备该动作的全局权限（Scope/RBAC），否则看资源 ACL 显式授予。
     *
     * @param context        主体上下文
     * @param permissionCode 所需权限编码
     * @param resourceType   资源类型（如 ASSET）
     * @param resourceId     资源业务 ID
     * @return 是否授予
     */
    public boolean isResourcePermitted(PrincipalContext context, String permissionCode,
                                       String resourceType, String resourceId) {
        if (context == null || context.principalId() == null) {
            return false;
        }
        if (isAgent(context) && Permissions.HIGH_RISK_ACTIONS.contains(permissionCode)) {
            return false;
        }
        if (isPermitted(context, permissionCode)) {
            return true;
        }
        return resourceAclRepository.hasPermission(
                resourceType, resourceId, context.principalId(), permissionCode);
    }

    /**
     * 防枚举资源授权：当主体对资源无权访问时，抛出与"资源不存在"相同语义的 {@link NotFoundException}，
     * 避免通过 403/404 差异枚举私有资源是否存在。
     *
     * @param permissionCode 所需权限编码
     * @param resourceType   资源类型
     * @param resourceId     资源业务 ID
     * @param notFoundCode   资源域对应的 NotFound 错误码（如 {@code ASSET_NOT_FOUND}）
     */
    public void authorizeResourceOrNotFound(String permissionCode, String resourceType,
                                            String resourceId, ErrorCode notFoundCode) {
        PrincipalContext context = PrincipalContextHolder.current().orElse(null);
        if (!isResourcePermitted(context, permissionCode, resourceType, resourceId)) {
            throw new NotFoundException(notFoundCode, "resource not found", Map.of());
        }
    }

    /**
     * 校验 Agent 是否被授权调用某 MCP 工具，否则抛出 403（工具不在白名单）。
     *
     * @param agentId  Agent 业务 ID
     * @param toolCode MCP 工具编码
     */
    public void requireToolAllowed(String agentId, String toolCode) {
        if (!agentToolRepository.isToolAllowed(agentId, toolCode)) {
            throw new AuthorizationException(ErrorCode.MCP_TOOL_NOT_ALLOWED,
                    "mcp tool not allowed", Map.of("tool", toolCode));
        }
    }

    /**
     * 计算当前主体的访问作用域，供资产等列表查询在数据库阶段下推过滤（避免先全量查再 Java 过滤）。
     *
     * @param resourceType ACL 资源类型（如 ASSET）
     * @param readAction   该资源域的读权限编码（如 {@code asset:read}）
     * @return 访问作用域
     */
    @Transactional(readOnly = true)
    public AccessScope computeAccessScope(String resourceType, String readAction) {
        PrincipalContext context = PrincipalContextHolder.current().orElse(null);
        if (context == null || context.principalId() == null) {
            return new AccessScope(null, false, Set.of(), Set.of(), Set.of());
        }
        String principalId = context.principalId();
        boolean platformAdmin = isPermitted(context, Permissions.AUTHORIZATION_MANAGE);
        // 组织作用域 = 角色绑定的组织作用域 ∪ 组织成员关系；成员关系经 organization 模块出站端口解析，
        // 在数据库阶段下推，供资产等列表查询按组织成员关系过滤。端口缺失时仅保留角色绑定作用域（fail-closed）。
        Set<String> organizationIds = new HashSet<>(roleBindingRepository.resolveOrganizationScopes(principalId));
        OrganizationMembershipPort membershipPort = membershipPortProvider == null
                ? null : membershipPortProvider.getIfAvailable();
        if (membershipPort != null) {
            organizationIds.addAll(membershipPort.findOrganizationIdsByPrincipal(principalId));
        }
        Set<String> projectIds = roleBindingRepository.resolveProjectScopes(principalId);
        Set<String> aclResourceIds = resourceAclRepository.accessibleResourceIds(
                resourceType, principalId, readAction);
        return new AccessScope(principalId, platformAdmin, organizationIds, projectIds, aclResourceIds);
    }

    private boolean isAgent(PrincipalContext context) {
        return context.principalType() == PrincipalType.AGENT;
    }

    private PrincipalContext currentContext() {
        return PrincipalContextHolder.current()
                .filter(ctx -> ctx.principalId() != null)
                .orElseThrow(() -> new AuthorizationException(
                        ErrorCode.AUTH_PERMISSION_DENIED, "no authenticated principal", Map.of()));
    }
}
