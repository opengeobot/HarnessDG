/*
 * 功能: 资产视图坐标字段单元测试。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.asset.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.aihub.asset.domain.Asset;
import com.aihub.asset.domain.AssetStatus;
import com.aihub.asset.domain.AssetType;
import com.aihub.asset.domain.ModelProfile;
import com.aihub.asset.domain.Visibility;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssetViewCoordinateTest {

    @Test
    void assetViewFromIncludesCoordinate() {
        Asset asset = Asset.create("ast_001", AssetType.MODEL, null, null, "nlp", "qwen-domain-7b",
                "Qwen", "desc", Visibility.PUBLIC, List.of("team-nlp"),
                List.of(), List.of(), "Apache-2.0", null,
                new ModelProfile("pytorch", "text-generation", null), null, "usr_01");

        AssetView view = AssetView.from(asset);

        assertThat(view.coordinate()).isEqualTo("aih://nlp/model/qwen-domain-7b");
    }

    @Test
    void assetSummaryViewIncludesCoordinateAndMatchedFields() {
        var summary = new com.aihub.asset.domain.AssetSummary(
                "ast_001", AssetType.DATASET, "vision", null, null, "defect-images",
                "Defect Images", "desc", Visibility.PUBLIC, AssetStatus.ACTIVE,
                List.of(), List.of(), List.of(), "CC-BY-4.0",
                null, null, "parquet", "image", Instant.now(),
                List.of("name", "taskCodes"));

        AssetSummaryView view = AssetSummaryView.from(summary);

        assertThat(view.coordinate()).isEqualTo("aih://vision/dataset/defect-images");
        assertThat(view.matchedFields()).containsExactly("name", "taskCodes");
    }
}
