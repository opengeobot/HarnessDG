/*
 * 功能: 资产访问策略，按可见性判定矩阵计算主体可见的可见性集合并判定单资产可访问性。
 * 时间: 2026-09-17
 * 作者: AxeXie
 */
package com.aihub.asset.application;

import com.aihub.asset.domain.Asset;
import com.aihub.asset.domain.Visibility;
import com.aihub.authorization.domain.AccessScope;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 资产访问策略。
 *
 * <p>提供权限下推所需的可见性集合与单资产可见性判断，确保检索阶段在数据库过滤、详情阶段防枚举。
 * 判定依据 {@code AC-P0B-AUTH-003}、{@code REQ-AUTH-002} 与 {@code Visibility} 枚举定义，
 * 与 {@code AssetSearchDao.appendAccessScopeFilter} 的 SQL 条件保持同一语义（TASK-P0BR-017）：
 *
 * <ul>
 *   <li>{@code PUBLIC}：任意已认证且状态正常的 Principal；</li>
 *   <li>{@code INTERNAL}：主体在资产所属组织或项目作用域内；</li>
 *   <li>{@code PRIVATE}：仅显式 ACL 授权或 Owner（Team 成员关系）。</li>
 * </ul>
 *
 * <p>默认拒绝：作用域缺失时不返回该可见性，绝不回退为"全部可见"。
 */
@Component
public class AssetAccessPolicy {

    /**
     * 计算主体可触及的可见性集合（作为 SQL {@code visibility IN (...)} 的收窄条件）。
     *
     * <p>该集合只表达"原则上可能可见"的可见性；逐行判定由 {@code AssetSearchDao} 的组织/项目/ACL/Owner
     * 条件精确完成。返回空集表示主体不可见任何资产。
     *
     * @param scope 主体访问作用域（可空 → 仅保留 PUBLIC 之外的默认拒绝结果）
     * @return 可见性集合
     */
    public Set<Visibility> visibleVisibilities(AccessScope scope) {
        Set<Visibility> visibilities = new LinkedHashSet<>();
        visibilities.add(Visibility.PUBLIC);
        if (scope == null) {
            return visibilities;
        }
        if (!scope.organizationIds().isEmpty() || !scope.projectIds().isEmpty()) {
            visibilities.add(Visibility.INTERNAL);
        }
        if (!scope.accessibleResourceIds().isEmpty() || !scope.teamIds().isEmpty()) {
            visibilities.add(Visibility.PRIVATE);
        }
        return visibilities;
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
    public boolean canAccess(Asset asset, AccessScope scope) {
        if (asset == null || asset.visibility() == null) {
            return false;
        }
        if (scope != null && scope.platformAdmin()) {
            return true;
        }
        return switch (asset.visibility()) {
            case PUBLIC -> true;
            case INTERNAL -> scope != null
                    && (contains(scope.organizationIds(), asset.organizationId())
                    || contains(scope.projectIds(), asset.projectId()));
            case PRIVATE -> scope != null
                    && (contains(scope.teamIds(), asset.ownerTeamId())
                    || contains(scope.accessibleResourceIds(), asset.assetId()));
        };
    }

    private static boolean contains(Set<String> values, String candidate) {
        return candidate != null && values != null && values.contains(candidate);
    }
}
