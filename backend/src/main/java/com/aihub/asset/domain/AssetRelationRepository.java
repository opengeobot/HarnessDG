/*
 * 功能: 资产血缘关系仓储端口。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.asset.domain;

import java.util.List;

/**
 * 资产血缘关系仓储端口。
 */
public interface AssetRelationRepository {

    /**
     * 按父资产 ID 查找直接关系（当前节点作为 parent）。
     */
    List<AssetRelation> findByParentAssetId(String parentAssetId);

    /**
     * 按子资产 ID 查找直接关系（当前节点作为 child）。
     */
    List<AssetRelation> findByChildAssetId(String childAssetId);

    /**
     * 持久化新的血缘关系边。
     */
    void insert(AssetRelation relation);

    /**
     * 按关系 ID 查找血缘关系边。
     */
    java.util.Optional<AssetRelation> findByRelationId(String relationId);

    /**
     * 按关系 ID 删除血缘关系边（硬删除；历史由 audit_log 保留）。
     *
     * @return 受影响行数（0 表示关系不存在）
     */
    int deleteByRelationId(String relationId);
}
