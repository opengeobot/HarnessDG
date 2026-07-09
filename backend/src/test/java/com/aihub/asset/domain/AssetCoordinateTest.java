/*
 * 功能: 资产坐标格式单元测试。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.asset.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AssetCoordinateTest {

    @Test
    void coordinateFormatsAihUriWithLowercaseType() {
        Asset model = Asset.create("ast_001", AssetType.MODEL, null, null, "nlp", "qwen-domain-7b",
                "Qwen", "desc", Visibility.PUBLIC, List.of("team-nlp"),
                List.of(), List.of(), "Apache-2.0", null,
                new ModelProfile("pytorch", "text-generation", null), null, "usr_01");

        assertThat(model.coordinate()).isEqualTo("aih://nlp/model/qwen-domain-7b");
    }

    @Test
    void coordinateFormatsDatasetTypeLowercased() {
        assertThat(Asset.formatCoordinate("vision", AssetType.DATASET, "defect-images"))
                .isEqualTo("aih://vision/dataset/defect-images");
    }
}
