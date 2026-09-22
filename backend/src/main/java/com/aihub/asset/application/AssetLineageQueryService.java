/*
 * 功能: 资产血缘查询服务——BFS 上下游遍历，深度可配置。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.asset.application;

import com.aihub.asset.domain.AssetRelation;
import com.aihub.asset.domain.AssetRelationRepository;
import com.aihub.asset.domain.AssetRepository;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.error.ValidationException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 资产血缘查询服务。
 */
@Service
public class AssetLineageQueryService {

    private static final int DEFAULT_DEPTH = 3;
    private static final int MAX_DEPTH = 10;

    private final AssetRepository assetRepository;
    private final AssetRelationRepository relationRepository;
    private final AssetAccessPolicy accessPolicy;
    private final AuthorizationService authorizationService;

    public AssetLineageQueryService(AssetRepository assetRepository,
                                    AssetRelationRepository relationRepository,
                                    AssetAccessPolicy accessPolicy,
                                    AuthorizationService authorizationService) {
        this.assetRepository = assetRepository;
        this.relationRepository = relationRepository;
        this.accessPolicy = accessPolicy;
        this.authorizationService = authorizationService;
    }

    /**
     * 查询资产血缘关系。
     *
     * @param assetId    起始资产 ID
     * @param depth      最大遍历深度（默认 3，上限 10）
     * @param direction  遍历方向：{@code up}（上游）或 {@code down}（下游）
     * @param principalId 当前主体 ID
     */
    @Transactional(readOnly = true)
    public AssetLineageView queryLineage(String assetId, Integer depth, String direction, String principalId) {
        authorizationService.requirePermission(Permissions.ASSET_READ);
        assetRepository.findByAssetId(assetId)
                .filter(asset -> accessPolicy.canAccess(asset, principalId))
                .orElseThrow(() -> new NotFoundException(
                        com.aihub.shared.error.ErrorCode.ASSET_NOT_FOUND,
                        "asset not found: " + assetId,
                        java.util.Map.of()));

        int resolvedDepth = depth == null ? DEFAULT_DEPTH : depth;
        if (resolvedDepth < 1 || resolvedDepth > MAX_DEPTH) {
            throw new ValidationException("depth must be between 1 and " + MAX_DEPTH);
        }
        String resolvedDirection = normalizeDirection(direction);

        List<AssetLineageView.RelationEdge> edges = traverse(assetId, resolvedDepth, resolvedDirection);
        return new AssetLineageView(assetId, resolvedDirection, resolvedDepth, edges);
    }

    private List<AssetLineageView.RelationEdge> traverse(String startAssetId, int maxDepth, String direction) {
        List<AssetLineageView.RelationEdge> result = new ArrayList<>();
        Set<String> visitedAssets = new HashSet<>();
        Set<String> seenRelations = new HashSet<>();
        Queue<Frontier> queue = new ArrayDeque<>();
        queue.add(new Frontier(startAssetId, 0));
        visitedAssets.add(startAssetId);

        while (!queue.isEmpty()) {
            Frontier current = queue.poll();
            if (current.depth() >= maxDepth) {
                continue;
            }
            List<AssetRelation> relations = "up".equals(direction)
                    ? relationRepository.findByChildAssetId(current.assetId())
                    : relationRepository.findByParentAssetId(current.assetId());

            for (AssetRelation relation : relations) {
                if (!seenRelations.add(relation.relationId())) {
                    continue;
                }
                int hopDepth = current.depth() + 1;
                result.add(AssetLineageView.RelationEdge.from(relation, hopDepth));

                String nextAssetId = "up".equals(direction)
                        ? relation.parentAssetId()
                        : relation.childAssetId();
                if (visitedAssets.add(nextAssetId)) {
                    queue.add(new Frontier(nextAssetId, hopDepth));
                }
            }
        }
        return result;
    }

    private static String normalizeDirection(String direction) {
        if (direction == null || direction.isBlank()) {
            return "down";
        }
        String normalized = direction.trim().toLowerCase();
        if (!"up".equals(normalized) && !"down".equals(normalized)) {
            throw new ValidationException("direction must be 'up' or 'down'");
        }
        return normalized;
    }

    private record Frontier(String assetId, int depth) {
    }
}
