/*
 * 功能: 资产访问策略，计算当前主体可见的资产可见性集合并下推到检索。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.application;

import com.aihub.asset.domain.Asset;
import com.aihub.asset.domain.Visibility;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 资产访问策略。
 *
 * <p>提供权限下推所需的可见性集合与单资产可见性判断，确保检索阶段在数据库过滤、详情阶段防枚举。
 *
 * <p><b>P1 现状</b>：身份与 RBAC 属 P3。在认证接入前，平台内部默认主体可见
 * {@code PUBLIC/INTERNAL/PRIVATE}，使目录功能可用；接入身份后此处依据角色/Owner/项目作用域收紧，
 * 调用方与 SQL 过滤机制保持不变。
 */
@Component
public class AssetAccessPolicy {

    /**
     * 计算当前主体可见的可见性集合。
     *
     * @param principalId 主体 ID（可空）
     * @return 可见性集合
     */
    public Set<Visibility> visibleVisibilities(String principalId) {
        return Set.of(Visibility.PUBLIC, Visibility.INTERNAL, Visibility.PRIVATE);
    }

    /**
     * 判断主体是否可访问指定资产（详情/防枚举）。
     *
     * @param asset       资产
     * @param principalId 主体 ID（可空）
     * @return 是否可访问
     */
    public boolean canAccess(Asset asset, String principalId) {
        return visibleVisibilities(principalId).contains(asset.visibility());
    }
}
