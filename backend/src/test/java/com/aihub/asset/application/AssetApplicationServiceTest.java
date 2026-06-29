/*
 * 功能: 资产应用服务单元测试——校验登记、查重、防枚举、更新、删除与检索编排。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.aihub.asset.domain.Asset;
import com.aihub.asset.domain.AssetRepository;
import com.aihub.asset.domain.AssetRepositoryProvisioner;
import com.aihub.asset.domain.AssetRepositoryRef;
import com.aihub.asset.domain.AssetSummary;
import com.aihub.asset.domain.AssetType;
import com.aihub.asset.domain.ModelProfile;
import com.aihub.asset.domain.Visibility;
import com.aihub.shared.api.CursorPage;
import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.error.PlatformException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AssetApplicationService} 离线单元测试，使用 Mockito 桩替依赖端口。
 */
@ExtendWith(MockitoExtension.class)
class AssetApplicationServiceTest {

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private AssetRepositoryProvisioner provisioner;

    @Mock
    private IdGenerator idGenerator;

    private AssetApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AssetApplicationService(assetRepository, provisioner, new AssetAccessPolicy(), idGenerator);
    }

    private CreateAssetCommand modelCommand() {
        return new CreateAssetCommand(AssetType.MODEL, "nlp", "qwen-domain-7b", "领域问答模型",
                "描述", Visibility.INTERNAL, List.of("team-nlp"), List.of("text-generation"),
                "Apache-2.0", new ModelProfile("pytorch", "text-generation", null), null, "usr_01");
    }

    private Asset storedModel() {
        Asset asset = Asset.create("ast_stored", AssetType.MODEL, "nlp", "qwen-domain-7b",
                "领域问答模型", "描述", Visibility.INTERNAL, List.of("team-nlp"),
                List.of("text-generation"), "Apache-2.0",
                new ModelProfile("pytorch", "text-generation", null), null, "usr_01");
        asset.attachRepository(new AssetRepositoryRef("nlp/qwen-domain-7b", null, null));
        return asset;
    }

    @Test
    void createAssetProvisionsRepositoryAndPersists() {
        given(assetRepository.existsByCoordinate("nlp", AssetType.MODEL, "qwen-domain-7b")).willReturn(false);
        given(idGenerator.generate(IdPrefix.ASSET)).willReturn("ast_new");
        given(provisioner.provision(any())).willReturn(new AssetRepositoryRef("nlp/qwen-domain-7b",
                "http://gitea/nlp/qwen-domain-7b", "http://gitea/nlp/qwen-domain-7b.git"));

        AssetView view = service.createAsset(modelCommand());

        assertThat(view.assetId()).isEqualTo("ast_new");
        assertThat(view.repository().fullName()).isEqualTo("nlp/qwen-domain-7b");
        assertThat(view.model().framework()).isEqualTo("pytorch");
        verify(assetRepository).insert(any(Asset.class));
    }

    @Test
    void createAssetRejectsDuplicateCoordinate() {
        given(idGenerator.generate(IdPrefix.ASSET)).willReturn("ast_new");
        given(assetRepository.existsByCoordinate("nlp", AssetType.MODEL, "qwen-domain-7b")).willReturn(true);

        assertThatThrownBy(() -> service.createAsset(modelCommand()))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((PlatformException) ex).errorCode())
                .isEqualTo(ErrorCode.ASSET_ALREADY_EXISTS);
    }

    @Test
    void getAssetThrowsNotFoundWhenMissing() {
        given(assetRepository.findByAssetId("ast_missing")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAsset("ast_missing", "usr_01"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getAssetReturnsViewWhenAccessible() {
        given(assetRepository.findByAssetId("ast_stored")).willReturn(Optional.of(storedModel()));

        AssetView view = service.getAsset("ast_stored", "usr_01");

        assertThat(view.namespace()).isEqualTo("nlp");
        assertThat(view.name()).isEqualTo("qwen-domain-7b");
    }

    @Test
    void updateAssetMutatesAndPersists() {
        given(assetRepository.findByAssetId("ast_stored")).willReturn(Optional.of(storedModel()));
        UpdateAssetCommand command = new UpdateAssetCommand("新名", "新描述", Visibility.PUBLIC,
                List.of("team-platform"), List.of("llm"), "MIT",
                new ModelProfile("vllm", "chat", null), null, "usr_02");

        AssetView view = service.updateAsset("ast_stored", command);

        assertThat(view.displayName()).isEqualTo("新名");
        assertThat(view.visibility()).isEqualTo(Visibility.PUBLIC);
        verify(assetRepository).update(any(Asset.class));
    }

    @Test
    void deleteAssetSoftDeletes() {
        given(assetRepository.findByAssetId("ast_stored")).willReturn(Optional.of(storedModel()));

        service.deleteAsset("ast_stored", "usr_09");

        verify(assetRepository).softDelete("ast_stored", "usr_09");
    }

    @Test
    void searchMapsSummariesToViews() {
        AssetSummary summary = new AssetSummary("ast_stored", AssetType.MODEL, "nlp", "qwen-domain-7b",
                "领域问答模型", "描述", Visibility.INTERNAL, com.aihub.asset.domain.AssetStatus.ACTIVE,
                List.of("team-nlp"), List.of("text-generation"), "Apache-2.0",
                "pytorch", "text-generation", null, null, Instant.now());
        given(assetRepository.search(any())).willReturn(new CursorPage<>(List.of(summary), "cursor-1", true));

        AssetSearchQuery query = new AssetSearchQuery("qwen", AssetType.MODEL, null, null, null,
                null, null, null, null, false, null, 20, "usr_01");
        CursorPage<AssetSummaryView> page = service.searchAssets(query);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).framework()).isEqualTo("pytorch");
        assertThat(page.nextCursor()).isEqualTo("cursor-1");
        assertThat(page.hasMore()).isTrue();
    }
}
