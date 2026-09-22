/*
 * 功能: AssetRelationApplicationService 单元测试——创建血缘关系与权限校验。
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
import com.aihub.audit.application.AuditService;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssetRelationApplicationServiceTest {

    @Mock private AssetRepository assetRepository;
    @Mock private AssetRelationRepository relationRepository;
    @Mock private AssetAccessPolicy accessPolicy;
    @Mock private AuthorizationService authorizationService;
    @Mock private AuditService auditService;
    @Mock private IdGenerator idGenerator;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-11T08:00:00Z"), ZoneOffset.UTC);
    private AssetRelationApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AssetRelationApplicationService(
                assetRepository, relationRepository, accessPolicy,
                authorizationService, auditService, idGenerator, clock);
    }

    @Test
    void createsRelationAndAudits() {
        Asset parent = sampleAsset("ast_parent");
        Asset child = sampleAsset("ast_child");
        when(assetRepository.findByAssetId("ast_parent")).thenReturn(Optional.of(parent));
        when(assetRepository.findByAssetId("ast_child")).thenReturn(Optional.of(child));
        when(accessPolicy.canAccess(parent, "usr_01")).thenReturn(true);
        when(accessPolicy.canAccess(child, "usr_01")).thenReturn(true);
        when(idGenerator.generate(IdPrefix.RELATION)).thenReturn("rel_test");

        AssetLineageView.RelationEdge edge = service.createRelation(
                "ast_parent", "ast_child", AssetRelationType.TRAINED_ON, "usr_01");

        assertThat(edge.relationId()).isEqualTo("rel_test");
        assertThat(edge.parentAssetId()).isEqualTo("ast_parent");
        assertThat(edge.childAssetId()).isEqualTo("ast_child");
        assertThat(edge.relationType()).isEqualTo(AssetRelationType.TRAINED_ON);

        ArgumentCaptor<AssetRelation> captor = ArgumentCaptor.forClass(AssetRelation.class);
        verify(relationRepository).insert(captor.capture());
        assertThat(captor.getValue().createdBy()).isEqualTo("usr_01");
        verify(authorizationService).requirePermission("asset:manage");
        verify(auditService).record(any());
    }

    @Test
    void rejectsSelfRelation() {
        Asset parent = sampleAsset("ast_parent");
        when(assetRepository.findByAssetId("ast_parent")).thenReturn(Optional.of(parent));
        when(accessPolicy.canAccess(parent, "usr_01")).thenReturn(true);

        assertThatThrownBy(() -> service.createRelation(
                "ast_parent", "ast_parent", AssetRelationType.DERIVED_FROM, "usr_01"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void deletesRelationAndAudits() {
        Asset parent = sampleAsset("ast_parent");
        when(assetRepository.findByAssetId("ast_parent")).thenReturn(Optional.of(parent));
        when(accessPolicy.canAccess(parent, "usr_01")).thenReturn(true);
        AssetRelation relation = new AssetRelation("rel_1", "ast_parent", "ast_child",
                AssetRelationType.TRAINED_ON, "usr_01", Instant.now());
        when(relationRepository.findByRelationId("rel_1")).thenReturn(Optional.of(relation));
        when(relationRepository.deleteByRelationId("rel_1")).thenReturn(1);

        service.deleteRelation("ast_parent", "rel_1", "usr_01");

        verify(relationRepository).deleteByRelationId("rel_1");
        verify(auditService).record(any());
    }

    @Test
    void deleteRelationRejectsWhenRelationNotBelongingToAsset() {
        Asset parent = sampleAsset("ast_parent");
        when(assetRepository.findByAssetId("ast_parent")).thenReturn(Optional.of(parent));
        when(accessPolicy.canAccess(parent, "usr_01")).thenReturn(true);
        AssetRelation foreign = new AssetRelation("rel_2", "ast_other", "ast_third",
                AssetRelationType.TRAINED_ON, "usr_01", Instant.now());
        when(relationRepository.findByRelationId("rel_2")).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.deleteRelation("ast_parent", "rel_2", "usr_01"))
                .isInstanceOf(com.aihub.shared.error.NotFoundException.class);
        verify(relationRepository, org.mockito.Mockito.never()).deleteByRelationId(any());
    }

    @Test
    void deleteRelationRejectsWhenRelationMissing() {
        Asset parent = sampleAsset("ast_parent");
        when(assetRepository.findByAssetId("ast_parent")).thenReturn(Optional.of(parent));
        when(accessPolicy.canAccess(parent, "usr_01")).thenReturn(true);
        when(relationRepository.findByRelationId("rel_missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteRelation("ast_parent", "rel_missing", "usr_01"))
                .isInstanceOf(com.aihub.shared.error.NotFoundException.class);
    }

    private static Asset sampleAsset(String assetId) {
        return Asset.create(assetId, AssetType.MODEL, null, null, "nlp", "demo", "Demo",
                null, Visibility.INTERNAL, null, null, null, "Apache-2.0", "team_nlp",
                null, null, "usr_01");
    }
}
