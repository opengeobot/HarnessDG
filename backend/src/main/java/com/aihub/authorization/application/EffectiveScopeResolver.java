/*
 * 功能: 有效 Scope 解析器——合并主体静态 Scope 与角色绑定权限编码，供登录/刷新签发 JWT 使用。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.authorization.application;

import com.aihub.authorization.domain.RoleBindingRepository;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 有效 Scope 解析器。
 *
 * <p>登录/刷新签发 JWT 时，Scope 来源为：
 * <ol>
 *   <li>主体静态 Scope（LocalUser.scopes / AgentIdentity.scopes）——引导种子与显式授予；</li>
 *   <li>角色绑定权限编码（role_binding → role → permission）——统一权限模型的真实来源。</li>
 * </ol>
 * 合并取并集，不缩小任何已授予范围。Bootstrap 管理员经 {@code rol_admin} 绑定获得全部管理 Scope，
 * 无需在静态 scopes 中穷举。
 */
@Component
public class EffectiveScopeResolver {

    private final RoleBindingRepository roleBindingRepository;

    public EffectiveScopeResolver(RoleBindingRepository roleBindingRepository) {
        this.roleBindingRepository = roleBindingRepository;
    }

    /**
     * 解析主体有效 Scope 集合 = 静态 scopes ∪ 角色绑定权限编码。
     *
     * @param principalId   主体 ID
     * @param staticScopes  主体静态 Scope（可空）
     * @return 合并后的有效 Scope 集合（不可空，空输入返回空集）
     */
    public Set<String> resolve(String principalId, Set<String> staticScopes) {
        Set<String> effective = new LinkedHashSet<>();
        if (staticScopes != null) {
            effective.addAll(staticScopes);
        }
        if (principalId != null && !principalId.isBlank()) {
            effective.addAll(roleBindingRepository.resolvePermissionCodes(principalId));
        }
        return effective;
    }
}
