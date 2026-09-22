/*
 * 功能: 资产访问策略，基于真实授权与 Team 成员关系计算可见性并下推到检索。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.application;

import com.aihub.asset.domain.Asset;
import com.aihub.asset.domain.Visibility;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.AccessScope;
import com.aihub.authorization.domain.Permissions;
import com.aihub.organization.domain.TeamRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * 资产访问策略。
 *
 * <p>提供权限下推所需的可见性集合与单资产可见性判断，确保检索阶段在数据库过滤、详情阶段防枚举。
 * 判定依据 {@code AC-P0B-AUTH-003}、{@code REQ-AUTH-002} 与 {@code Visibility} 枚举定义，
 * 与 {@code AssetSearchDao.appendAccessScopeFilter} 的 SQL 条件保持同一语义（TASK-P0BR-017）：
 *
 * <p>可见性计算基于 {@link AuthorizationService} 的 RBAC + Scope 判定：
 * <ul>
 *   <li>无认证 → 空集（fail closed）</li>
 *   <li>有 {@code asset:read} → PUBLIC + INTERNAL</li>
 *   <li>有 {@code asset:read} + 组织作用域或平台管理员 → 加上 PRIVATE（SQL 层再按 Owner/ACL/Org 过滤）</li>
 *   <li>有 {@code asset:manage} → 全部</li>
 * </ul>
 *
 * <p>PRIVATE 资产详情访问：除平台 {@code asset:manage} 外，Owner Team 成员或显式 ACL 主体可访问。
 */
@Component
public class AssetAccessPolicy {

    private final AuthorizationService authorizationService;
    private final TeamRepository teamRepository;

    public AssetAccessPolicy(AuthorizationService authorizationService,
                             TeamRepository teamRepository) {
        this.authorizationService = authorizationService;
        this.teamRepository = teamRepository;
    }

    /**
     * 计算主体可触及的可见性集合（作为 SQL {@code visibility IN (...)} 的收窄条件）。
     *
     * <p>该集合只表达"原则上可能可见"的可见性；逐行判定由 {@code AssetSearchDao} 的组织/项目/ACL/Owner
     * 条件精确完成。返回空集表示主体不可见任何资产。
     *
     * @param scope 主体访问作用域（可空 → 仅保留 PUBLIC 之外的默认拒绝结果）
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
     * <p>与列表下推条件等价；无权时调用方应抛出与"资源不存在"相同的 {@code ASSET_NOT_FOUND}。
     *
     * @param asset 资产
     * @param scope 主体访问作用域（可空 → 默认拒绝除 PUBLIC 外的资产）
     * @return 是否可访问
     */
    public boolean canAccess(Asset asset, String principalId) {
        if (!visibleVisibilities(principalId).contains(asset.visibility())) {
            return false;
        }
        if (asset.visibility() == Visibility.PRIVATE) {
            if (authorizationService.isPermitted(Permissions.ASSET_MANAGE)) {
                return true;
            }
            if (isOwnerTeamMember(asset, principalId)) {
                return true;
            }
            // legacy owners 列表只读回显，不再作为写入来源；读路径保留兼容
            if (asset.owners() != null && asset.owners().contains(principalId)) {
                return true;
            }
            return authorizationService.isResourcePermitted(
                    buildPrincipalContext(principalId), Permissions.ASSET_READ, "ASSET", asset.assetId());
        }
        return true;
    }

    private boolean isOwnerTeamMember(Asset asset, String principalId) {
        if (!StringUtils.hasText(asset.ownerTeamId()) || principalId == null) {
            return false;
        }
        return teamRepository.findMember(asset.ownerTeamId(), principalId).isPresent();
    }

    private static com.aihub.shared.identity.PrincipalContext buildPrincipalContext(String principalId) {
        return new com.aihub.shared.identity.PrincipalContext(
                principalId, null, null, null, null, null, null, 0, null, null, null);
    }
}
