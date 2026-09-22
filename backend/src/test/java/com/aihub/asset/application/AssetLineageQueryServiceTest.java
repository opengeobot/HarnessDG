/*
 * 功能: AssetLineageQueryService 单元测试——BFS 上下游遍历与深度限制。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.asset.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aihub.asset.domain.Asset;
import com.aihub.asset.domain.AssetRelation;
import com.aihub.asset.domain.AssetRelationRepository;
import com.aihub.asset.domain.AssetRelationType;
import com.aihub.asset.domain.AssetRepository;
import com.aihub.asset.domain.AssetStatus;
import com.aihub.asset.domain.AssetType;
import com.aihub.asset.domain.Visibility;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.error.ValidationException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssetLineageQueryServiceTest {

    @Mock private AssetRepository assetRepository;
    @Mock private AssetRelationRepository relationRepository;
    @Mock private AssetAccessPolicy accessPolicy;
    @Mock private AuthorizationService authorizationService;

    private AssetLineageQueryService service;

    @BeforeEach
    void setUp() {
        service = new AssetLineageQueryService(assetRepository, relationRepository, accessPolicy, authorizationService);
    }

    @Test
    void traversesDownstreamWithDepthLimit() {
        Asset root = sampleAsset("ast_root");
        when(assetRepository.findByAssetId("ast_root")).thenReturn(Optional.of(root));
        when(accessPolicy.canAccess(root, "usr_01")).thenReturn(true);
        when(relationRepository.findByParentAssetId("ast_root")).thenReturn(List.of(
                relation("rel_1", "ast_root", "ast_child", AssetRelationType.TRAINED_ON)));
        when(relationRepository.findByParentAssetId("ast_child")).thenReturn(List.of(
                relation("rel_2", "ast_child", "ast_grand", AssetRelationType.DERIVED_FROM)));

        AssetLineageView view = service.queryLineage("ast_root", 2, "down", "usr_01");

        assertThat(view.direction()).isEqualTo("down");
        assertThat(view.depth()).isEqualTo(2);
        assertThat(view.relations()).hasSize(2);
        assertThat(view.relations().get(0).hopDepth()).isEqualTo(1);
        assertThat(view.relations().get(1).childAssetId()).isEqualTo("ast_grand");
        verify(authorizationService).requirePermission("asset:read");
    }

    @Test
    void traversesUpstream() {
        Asset child = sampleAsset("ast_child");
        when(assetRepository.findByAssetId("ast_child")).thenReturn(Optional.of(child));
        when(accessPolicy.canAccess(child, "usr_01")).thenReturn(true);
        when(relationRepository.findByChildAssetId("ast_child")).thenReturn(List.of(
                relation("rel_1", "ast_parent", "ast_child", AssetRelationType.BASED_ON)));
        when(relationRepository.findByChildAssetId("ast_parent")).thenReturn(List.of());

        AssetLineageView view = service.queryLineage("ast_child", 3, "up", "usr_01");

        assertThat(view.direction()).isEqualTo("up");
        assertThat(view.relations()).hasSize(1);
        assertThat(view.relations().get(0).parentAssetId()).isEqualTo("ast_parent");
    }

    @Test
    void rejectsInvalidDirection() {
        Asset asset = sampleAsset("ast_root");
        when(assetRepository.findByAssetId("ast_root")).thenReturn(Optional.of(asset));
        when(accessPolicy.canAccess(asset, "usr_01")).thenReturn(true);

        assertThatThrownBy(() -> service.queryLineage("ast_root", 3, "sideways", "usr_01"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void hidesInaccessibleAsset() {
        Asset asset = sampleAsset("ast_private");
        when(assetRepository.findByAssetId("ast_private")).thenReturn(Optional.of(asset));
        when(accessPolicy.canAccess(asset, "usr_01")).thenReturn(false);

        assertThatThrownBy(() -> service.queryLineage("ast_private", 3, "down", "usr_01"))
                .isInstanceOf(NotFoundException.class);
    }

    private static Asset sampleAsset(String assetId) {
        return Asset.create(assetId, AssetType.MODEL, null, null, "nlp", "demo", "Demo",
                null, Visibility.INTERNAL, null, null, null, "Apache-2.0", "team_nlp",
                null, null, "usr_01");
    }

    private static AssetRelation relation(String id, String parent, String child, AssetRelationType type) {
        return new AssetRelation(id, parent, child, type, "usr_01", Instant.parse("2026-07-11T00:00:00Z"));
    }
}
