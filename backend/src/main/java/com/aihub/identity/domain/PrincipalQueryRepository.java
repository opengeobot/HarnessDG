/*
 * 功能: 主体查询端口，支持按类型与关键字检索统一主体。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.domain;

import com.aihub.shared.identity.PrincipalType;
import java.util.List;

/**
 * 主体查询端口。用于 {@code /system/principals} 统一主体检索（只读投影）。
 */
public interface PrincipalQueryRepository {

    /**
     * 按可选类型与关键字检索主体摘要。
     *
     * @param principalType 主体类型，{@code null} 表示不限
     * @param keyword       展示名/主体 ID 关键字，{@code null} 表示不限
     * @return 主体摘要列表
     */
    List<PrincipalAccount> search(PrincipalType principalType, String keyword);

    /**
     * 判断给定主体 ID 是否存在（用于跨模块校验主体存在性，如添加组织成员）。
     *
     * @param principalId 主体 ID
     * @return 是否存在
     */
    boolean existsByPrincipalId(String principalId);
}
