/*
 * 功能: 资产血缘查询视图。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.asset.application;

import com.aihub.asset.domain.AssetRelation;
import com.aihub.asset.domain.AssetRelationType;
import java.time.Instant;
import java.util.List;

/**
 * 资产血缘查询结果。
 *
 * @param assetId   起始资产 ID
 * @param direction 遍历方向（up=上游 / down=下游）
 * @param depth     最大遍历深度
 * @param relations 命中的关系边（按 BFS 层级顺序）
 */
public record AssetLineageView(String assetId,
                               String direction,
                               int depth,
                               List<RelationEdge> relations) {

    /**
     * 血缘关系边视图。
     */
    public record RelationEdge(String relationId,
                               String parentAssetId,
                               String childAssetId,
                               AssetRelationType relationType,
                               int hopDepth,
                               String createdBy,
                               Instant createdAt) {

        static RelationEdge from(AssetRelation relation, int hopDepth) {
            return new RelationEdge(
                    relation.relationId(),
                    relation.parentAssetId(),
                    relation.childAssetId(),
                    relation.relationType(),
                    hopDepth,
                    relation.createdBy(),
                    relation.createdAt());
        }
    }
}
