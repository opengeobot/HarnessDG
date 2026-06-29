/*
 * 功能: 资产聚合单元测试——校验坐标不变量、元数据修改、状态流转与卡片渲染。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link Asset} 与 {@link AssetCard} 离线单元测试。
 */
class AssetTest {

    private Asset newModel() {
        return Asset.create("ast_01", AssetType.MODEL, "nlp", "qwen-domain-7b",
                "领域问答模型", "一个领域问答模型", Visibility.INTERNAL,
                List.of("team-nlp"), List.of("text-generation"), "Apache-2.0",
                new ModelProfile("pytorch", "text-generation", "decoder-only"), null, "usr_01");
    }

    @Test
    void createsModelWithActiveStatusAndModelProfile() {
        Asset asset = newModel();

        assertThat(asset.status()).isEqualTo(AssetStatus.ACTIVE);
        assertThat(asset.modelProfile().framework()).isEqualTo("pytorch");
        assertThat(asset.datasetProfile()).isNull();
        assertThat(asset.tags()).containsExactly("text-generation");
    }

    @Test
    void rejectsInvalidSlug() {
        assertThatThrownBy(() -> Asset.create("ast_02", AssetType.MODEL, "NLP", "Bad Name",
                null, null, Visibility.PUBLIC, null, null, null, null, null, "usr_01"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void datasetIgnoresModelProfileAndKeepsDatasetProfile() {
        Asset dataset = Asset.create("ast_03", AssetType.DATASET, "vision", "defect-images",
                "缺陷图像", null, Visibility.PRIVATE, null, List.of("vision", "vision"), null,
                new ModelProfile("pytorch", null, null), new DatasetProfile("parquet", "image"), "usr_01");

        assertThat(dataset.modelProfile()).isNull();
        assertThat(dataset.datasetProfile().format()).isEqualTo("parquet");
        // 去重后标签仅保留一个。
        assertThat(dataset.tags()).containsExactly("vision");
    }

    @Test
    void updateMetadataKeepsCoordinateImmutableAndChangesStatusViaDeprecate() {
        Asset asset = newModel();
        asset.updateMetadata("新名称", "新描述", Visibility.PUBLIC,
                List.of("team-platform"), List.of("llm"), "MIT",
                new ModelProfile("vllm", "chat", "moe"), null, "usr_02");

        assertThat(asset.namespace()).isEqualTo("nlp");
        assertThat(asset.name()).isEqualTo("qwen-domain-7b");
        assertThat(asset.displayName()).isEqualTo("新名称");
        assertThat(asset.visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(asset.modelProfile().framework()).isEqualTo("vllm");

        asset.deprecate("usr_03");
        assertThat(asset.status()).isEqualTo(AssetStatus.DEPRECATED);
    }

    @Test
    void rendersCardWithReadmeAndAssetYaml() {
        AssetCard.CardFiles card = AssetCard.render(newModel());

        assertThat(card.readme()).contains("# 领域问答模型").contains("text-generation");
        assertThat(card.assetYaml())
                .contains("schemaVersion: aihub/v1")
                .contains("kind: Model")
                .contains("namespace: nlp")
                .contains("name: qwen-domain-7b")
                .contains("framework: pytorch");
    }
}
