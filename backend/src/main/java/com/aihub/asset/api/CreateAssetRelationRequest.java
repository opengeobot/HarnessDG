/*
 * 功能: 创建资产血缘关系请求体。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.asset.api;

import com.aihub.asset.domain.AssetRelationType;

/**
 * 创建资产血缘关系请求。
 *
 * @param childAssetId 下游/子资产 ID
 * @param relationType 关系类型
 */
public record CreateAssetRelationRequest(String childAssetId, AssetRelationType relationType) {
}
