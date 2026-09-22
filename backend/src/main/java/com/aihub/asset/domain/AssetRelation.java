/*
 * 功能: 资产血缘关系领域模型。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.asset.domain;

import java.time.Instant;

/**
 * 资产血缘关系边。
 *
 * @param relationId     关系业务 ID
 * @param parentAssetId  上游/父资产 ID
 * @param childAssetId   下游/子资产 ID
 * @param relationType   关系类型
 * @param createdBy      创建者主体 ID
 * @param createdAt      创建时间
 */
public record AssetRelation(String relationId,
                            String parentAssetId,
                            String childAssetId,
                            AssetRelationType relationType,
                            String createdBy,
                            Instant createdAt) {
}
