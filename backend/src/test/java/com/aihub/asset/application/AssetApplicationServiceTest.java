/*
 * 功能: 资产应用服务单元测试——校验登记/查询/更新/删除用例与治理接入（授权/字典/标签/审计）。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aihub.asset.domain.Asset;
import com.aihub.asset.domain.AssetCardProjectionPort;
import com.aihub.asset.domain.AssetRepository;
import com.aihub.asset.domain.AssetStatus;
import com.aihub.asset.domain.AssetType;
import com.aihub.asset.domain.ModelProfile;
import com.aihub.asset.domain.ProvisioningStatus;
import com.aihub.asset.domain.Visibility;
import com.aihub.audit.application.AuditService;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.AccessScope;
import com.aihub.job.domain.JobRepository;
import com.aihub.shared.api.CursorPage;
import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.taxonomy.dictionary.application.DictionaryValidationPort;
import com.aihub.taxonomy.domain.TaxonomyStatus;
import com.aihub.taxonomy.tag.application.TagDtos.TagScopeContext;
import com.aihub.taxonomy.tag.application.TagDtos.TagView;
import com.aihub.taxonomy.tag.application.TagValidationService;
import com.aihub.taxonomy.tag.domain.TagScopeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

/**
 * {@link AssetApplicationService} 单元测试。
 *
 * <p>验证核心用例编排，同时验证治理接入：授权前置、字典治理字段校验、受控标签校验、审计记录。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssetApplicationServiceTest {

    @Mock private AssetRepository assetRepository;
    @Mock private IdGenerator idGenerator;
    @Mock private AuthorizationService authorizationService;
    @Mock private DictionaryValidationPort dictionaryValidationPort;
    @Mock private TagValidationService tagValidationService;
    @Mock private AuditService auditService;
    @Mock private JobRepository jobRepository;
    @Mock private ObjectProvider<AssetCardProjectionPort> cardProjectionPortProvider;

    private AssetApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AssetApplicationService(assetRepository,
                new AssetAccessPolicy(authorizationService), idGenerator, authorizationService,
                dictionaryValidationPort, tagValidationService, auditService, jobRepository,
                cardProjectionPortProvider);
        when(idGenerator.generate(any(IdPrefix.class))).thenReturn("ast_generated");
        when(assetRepository.existsByCoordinate(any(), any(), any())).thenReturn(false);
        doNothing().when(assetRepository).insert(any(Asset.class));
        // AssetAccessPolicy 需要授权 mock：isPermitted + computeAccessScope
        when(authorizationService.isPermitted(any())).thenReturn(true);
        when(authorizationService.computeAccessScope(any(), any()))
                .thenReturn(new AccessScope("usr_01", false, Set.of(), Set.of(), Set.of()));
    }

    private CreateAssetCommand modelCommand() {
        return new CreateAssetCommand(AssetType.MODEL, null, null, "nlp", "qwen-domain-7b",
                "领域问答模型", "描述", Visibility.INTERNAL, List.of("team-nlp"),
                List.of("text-generation"), null, "Apache-2.0",
                new ModelProfile("pytorch", "text-generation", null), null, "usr_01");
    }

    private Asset storedModel() {
        return Asset.create("ast_stored", AssetType.MODEL, null, null, "nlp", "qwen-domain-7b",
                "领域问答模型", "描述", Visibility.INTERNAL, List.of("team-nlp"),
                List.of("text-generation"), null, "Apache-2.0",
                new ModelProfile("pytorch", "text-generation", null), null, "usr_01");
    }

    @Test
    void createsAssetAndProvisionsRepository() {
        AssetView view = service.createAsset(modelCommand());

        assertThat(view.assetId()).isEqualTo("ast_generated");
        assertThat(view.provisioningStatus()).isEqualTo(ProvisioningStatus.PENDING);
        verify(authorizationService).requirePermission("asset:create");
        verify(assetRepository).insert(any(Asset.class));
        verify(jobRepository).insert(any());
        verify(auditService).record(any());
    }

    @Test
    void getAssetReturnsNotFoundForMissingOrInaccessible() {
        when(assetRepository.findByAssetId("ast_missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAsset("ast_missing", "usr_01"))
                .isInstanceOf(NotFoundException.class);
        verify(authorizationService).requirePermission("asset:read");
    }

    @Test
    void updateAssetChangesMutableMetadata() {
        Asset asset = storedModel();
        when(assetRepository.findByAssetId("ast_stored")).thenReturn(Optional.of(asset));
        doNothing().when(assetRepository).update(any(Asset.class));

        UpdateAssetCommand command = new UpdateAssetCommand(null, null, "新名", "新描述",
                Visibility.PUBLIC, List.of("team-platform"), List.of("llm"), null, "MIT",
                new ModelProfile("vllm", "chat", null), null, "usr_02");
        AssetView view = service.updateAsset("ast_stored", command);

        assertThat(view.displayName()).isEqualTo("新名");
        verify(authorizationService).requirePermission("asset:update");
        verify(auditService).record(any());
    }

    @Test
    void deleteAssetSoftDeletesAndAudits() {
        Asset asset = storedModel();
        when(assetRepository.findByAssetId("ast_stored")).thenReturn(Optional.of(asset));

        service.deleteAsset("ast_stored", "usr_01");

        verify(assetRepository).softDelete("ast_stored", "usr_01");
        verify(auditService).record(any());
    }

    @Test
    void searchAssetsReturnsSummaryPage() {
        com.aihub.asset.domain.AssetSummary summary = new com.aihub.asset.domain.AssetSummary(
                "ast_stored", AssetType.MODEL, "nlp", null, null, "qwen-domain-7b",
                "领域问答模型", "描述", Visibility.INTERNAL, AssetStatus.ACTIVE,
                List.of("team-nlp"), List.of("text-generation"), List.of(), "Apache-2.0",
                "pytorch", "text-generation", null, null, Instant.now());
        when(assetRepository.search(any())).thenReturn(new CursorPage<>(List.of(summary), null, false));

        AssetSearchQuery query = new AssetSearchQuery("qwen", AssetType.MODEL, null, null, null, null,
                null, null, null, null, null, null, false, null, 20, "usr_01");
        CursorPage<AssetSummaryView> page = service.searchAssets(query);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).name()).isEqualTo("qwen-domain-7b");
        verify(authorizationService).requirePermission("asset:read");
    }

    @Test
    void createAssetValidatesDictionaryGovernanceFields() {
        service.createAsset(modelCommand());

        verify(dictionaryValidationPort).validateItemCode(eq("license_catalog"), eq("Apache-2.0"));
        verify(dictionaryValidationPort).validateItemCode(eq("model_framework"), eq("pytorch"));
        verify(dictionaryValidationPort).validateItemCode(eq("model_task"), eq("text-generation"));
    }

    @Test
    void createAssetRejectsDisabledDictionaryItem() {
        doThrow(new ValidationException("dict item not active"))
                .when(dictionaryValidationPort).validateItemCode("license_catalog", "Bad-License");

        CreateAssetCommand command = new CreateAssetCommand(AssetType.MODEL, null, null, "nlp",
                "bad-model", "Bad Model", null, Visibility.INTERNAL, List.of("team-nlp"), null, null,
                "Bad-License", new ModelProfile("pytorch", "text-generation", null), null, "usr_01");

        assertThatThrownBy(() -> service.createAsset(command))
                .isInstanceOf(ValidationException.class);
        verify(assetRepository, never()).insert(any(Asset.class));
    }

    @Test
    void createAssetResolvesTagIdsViaTagValidationService() {
        when(tagValidationService.resolveActiveTags(any(), any()))
                .thenReturn(List.of(new TagView("tag_001", TagScopeType.PLATFORM, "PLATFORM",
                        "nlp", "NLP", null, null, TaxonomyStatus.ACTIVE, 1L)));

        CreateAssetCommand command = new CreateAssetCommand(AssetType.MODEL, null, null, "nlp",
                "tagged-model", "Tagged Model", null, Visibility.INTERNAL, List.of("team-nlp"),
                null, List.of("tag_001"), "Apache-2.0",
                new ModelProfile("pytorch", "text-generation", null), null, "usr_01");
        AssetView view = service.createAsset(command);

        verify(tagValidationService).resolveActiveTags(eq(List.of("tag_001")), any(TagScopeContext.class));
        assertThat(view.tagIds()).containsExactly("tag_001");
    }

    @Test
    void createAssetRejectsUnregisteredTagIds() {
        when(tagValidationService.resolveActiveTags(any(), any()))
                .thenThrow(new ValidationException("tag not found: tag_unknown"));

        CreateAssetCommand command = new CreateAssetCommand(AssetType.MODEL, null, null, "nlp",
                "bad-tag-model", "Bad Tag Model", null, Visibility.INTERNAL, List.of("team-nlp"),
                null, List.of("tag_unknown"), "Apache-2.0",
                new ModelProfile("pytorch", "text-generation", null), null, "usr_01");

        assertThatThrownBy(() -> service.createAsset(command))
                .isInstanceOf(ValidationException.class);
        verify(assetRepository, never()).insert(any(Asset.class));
    }

    @Test
    void auditFailureDoesNotBlockCreateAsset() {
        doThrow(new RuntimeException("audit unavailable"))
                .when(auditService).record(any());

        AssetView view = service.createAsset(modelCommand());

        assertThat(view.assetId()).isEqualTo("ast_generated");
        verify(assetRepository).insert(any(Asset.class));
    }

    @Test
    void deprecateAssetTransitionsToDeprecated() {
        Asset asset = storedModel();
        when(assetRepository.findByAssetId("ast_stored")).thenReturn(Optional.of(asset));

        AssetView view = service.deprecateAsset("ast_stored", "usr_01");

        assertThat(view.status()).isEqualTo(AssetStatus.DEPRECATED);
        verify(assetRepository).update(any(Asset.class));
        verify(auditService).record(any());
    }

    @Test
    void archiveAssetTransitionsToArchived() {
        Asset asset = storedModel();
        asset.deprecate("usr_01");
        when(assetRepository.findByAssetId("ast_stored")).thenReturn(Optional.of(asset));

        AssetView view = service.archiveAsset("ast_stored", "usr_01");

        assertThat(view.status()).isEqualTo(AssetStatus.ARCHIVED);
        verify(assetRepository).update(any(Asset.class));
        verify(auditService).record(any());
    }

    @Test
    void restoreAssetTransitionsToActive() {
        Asset asset = storedModel();
        asset.deprecate("usr_01");
        when(assetRepository.findByAssetId("ast_stored")).thenReturn(Optional.of(asset));

        AssetView view = service.restoreAsset("ast_stored", "usr_01");

        assertThat(view.status()).isEqualTo(AssetStatus.ACTIVE);
        verify(assetRepository).update(any(Asset.class));
        verify(auditService).record(any());
    }

    @Test
    void deprecateArchivedAssetThrowsStateNotAllowed() {
        Asset asset = storedModel();
        asset.archive("usr_01");
        when(assetRepository.findByAssetId("ast_stored")).thenReturn(Optional.of(asset));

        assertThatThrownBy(() -> service.deprecateAsset("ast_stored", "usr_01"))
                .isInstanceOf(ConflictException.class);
    }
}
