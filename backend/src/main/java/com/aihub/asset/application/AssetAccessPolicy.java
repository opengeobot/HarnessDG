/*
 * 功能: 资产访问策略，基于真实授权计算当前主体可见的资产可见性集合并下推到检索。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.application;

import com.aihub.asset.domain.Asset;
import com.aihub.asset.domain.Visibility;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.AccessScope;
import com.aihub.authorization.domain.Permissions;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 资产访问策略。
 *
 * <p>提供权限下推所需的可见性集合与单资产可见性判断，确保检索阶段在数据库过滤、详情阶段防枚举。
 *
 * <p>可见性计算基于 {@link AuthorizationService} 的 RBAC + Scope 判定：
 * <ul>
 *   <li>无认证 → 空集（fail closed）</li>
 *   <li>有 {@code asset:read} → PUBLIC + INTERNAL</li>
 *   <li>有 {@code asset:read} + 组织作用域或平台管理员 → 加上 PRIVATE（SQL 层再按 Owner/ACL/Org 过滤）</li>
 *   <li>有 {@code asset:manage} → 全部</li>
 * </ul>
 */
@Component
public class AssetAccessPolicy {

    private final AuthorizationService authorizationService;

    public AssetAccessPolicy(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    /**
     * 计算当前主体可见的可见性集合。
     *
     * @param principalId 主体 ID（可空）
     * @return 可见性集合
     */
    public Set<Visibility> visibleVisibilities(String principalId) {
        if (principalId == null) {
            return Set.of();
        }
        if (!authorizationService.isPermitted(Permissions.ASSET_READ)) {
            return Set.of();
        }
        Set<Visibility> result = new HashSet<>();
        result.add(Visibility.PUBLIC);
        result.add(Visibility.INTERNAL);
        // PRIVATE 可见性要求主体有组织作用域、平台管理员或 asset:manage 权限；
        // 实际 PRIVATE 资源的 Owner/ACL/Org 过滤在 SQL 层由 AccessScope 下推。
        if (authorizationService.isPermitted(Permissions.ASSET_MANAGE)) {
            result.add(Visibility.PRIVATE);
        } else {
            AccessScope scope = authorizationService.computeAccessScope("ASSET", Permissions.ASSET_READ);
            if (scope.platformAdmin() || !scope.organizationIds().isEmpty()
                    || !scope.accessibleResourceIds().isEmpty()) {
                result.add(Visibility.PRIVATE);
            }
        }
        return Set.copyOf(result);
    }

    /**
     * 判断主体是否可访问指定资产（详情/防枚举）。
     *
     * <p>对 PRIVATE 资产额外检查 Owner 成员关系；组织/ACL 过滤由 SQL 层 AccessScope 保障。
     *
     * @param asset       资产
     * @param principalId 主体 ID（可空）
     * @return 是否可访问
     */
    public boolean canAccess(Asset asset, String principalId) {
        if (!visibleVisibilities(principalId).contains(asset.visibility())) {
            return false;
        }
        if (asset.visibility() == Visibility.PRIVATE) {
            if (asset.owners() != null && asset.owners().contains(principalId)) {
                return true;
            }
            // 平台管理员或 asset:manage 可见所有 PRIVATE
            return authorizationService.isPermitted(Permissions.ASSET_MANAGE);
        }
        return true;
    }
}
