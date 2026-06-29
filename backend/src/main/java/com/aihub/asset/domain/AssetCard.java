/*
 * 功能: 资产卡片渲染领域服务，由资产元数据生成 README.md 与机器可读 asset.yaml。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.domain;

import java.util.List;

/**
 * 资产卡片渲染。
 *
 * <p>依据设计 6.2 节，仓库以 {@code README.md} 作为人类说明、{@code asset.yaml} 作为机器事实。
 * 此处由资产元数据渲染初始卡片内容，供仓库开通时写入；后续以仓库为事实源演进。
 */
public final class AssetCard {

    private static final String SCHEMA_VERSION = "aihub/v1";

    private AssetCard() {
    }

    /**
     * 渲染资产初始卡片文件。
     *
     * @param asset 资产聚合
     * @return 卡片文件集合（README.md 与 asset.yaml）
     */
    public static CardFiles render(Asset asset) {
        return new CardFiles(renderReadme(asset), renderAssetYaml(asset));
    }

    private static String renderReadme(Asset asset) {
        String kind = asset.type() == AssetType.MODEL ? "模型" : "数据集";
        StringBuilder readme = new StringBuilder();
        readme.append("# ").append(displayTitle(asset)).append('\n').append('\n');
        readme.append("> ").append(kind).append("卡片 · `")
                .append(asset.namespace()).append('/').append(asset.name()).append("`\n\n");
        readme.append("## 概述\n\n")
                .append(asset.description() == null || asset.description().isBlank()
                        ? "_待补充_" : asset.description())
                .append("\n\n");
        readme.append("## 元数据\n\n");
        readme.append("- 类型: ").append(asset.type()).append('\n');
        readme.append("- 可见性: ").append(asset.visibility()).append('\n');
        readme.append("- Owner: ").append(joinOrPlaceholder(asset.owners())).append('\n');
        readme.append("- 标签: ").append(joinOrPlaceholder(asset.tags())).append('\n');
        readme.append("- 许可证: ").append(orPlaceholder(asset.license())).append('\n');
        return readme.toString();
    }

    private static String renderAssetYaml(Asset asset) {
        StringBuilder yaml = new StringBuilder();
        yaml.append("schemaVersion: ").append(SCHEMA_VERSION).append('\n');
        yaml.append("kind: ").append(asset.type() == AssetType.MODEL ? "Model" : "Dataset").append('\n');
        yaml.append("metadata:\n");
        yaml.append("  namespace: ").append(asset.namespace()).append('\n');
        yaml.append("  name: ").append(asset.name()).append('\n');
        appendScalar(yaml, "  displayName: ", asset.displayName());
        yaml.append("  visibility: ").append(asset.visibility().name().toLowerCase()).append('\n');
        appendSequence(yaml, "  owners:", asset.owners());
        appendSequence(yaml, "  tags:", asset.tags());
        yaml.append("spec:\n");
        if (asset.type() == AssetType.MODEL) {
            ModelProfile profile = asset.modelProfile() == null ? ModelProfile.empty() : asset.modelProfile();
            appendScalar(yaml, "  framework: ", profile.framework());
            appendScalar(yaml, "  task: ", profile.task());
            appendScalar(yaml, "  architecture: ", profile.architecture());
        } else {
            DatasetProfile profile = asset.datasetProfile() == null
                    ? DatasetProfile.empty() : asset.datasetProfile();
            appendScalar(yaml, "  format: ", profile.format());
            appendScalar(yaml, "  modality: ", profile.modality());
        }
        appendScalar(yaml, "  license: ", asset.license());
        return yaml.toString();
    }

    private static String displayTitle(Asset asset) {
        return asset.displayName() == null || asset.displayName().isBlank()
                ? asset.name() : asset.displayName();
    }

    private static void appendScalar(StringBuilder yaml, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        yaml.append(key).append(quoteIfNeeded(value)).append('\n');
    }

    private static void appendSequence(StringBuilder yaml, String key, List<String> values) {
        yaml.append(key).append('\n');
        if (values == null || values.isEmpty()) {
            return;
        }
        for (String value : values) {
            yaml.append("    - ").append(quoteIfNeeded(value)).append('\n');
        }
    }

    private static String quoteIfNeeded(String value) {
        boolean safe = value.matches("^[A-Za-z0-9._/@-]+$");
        return safe ? value : "\"" + value.replace("\"", "\\\"") + "\"";
    }

    private static String joinOrPlaceholder(List<String> values) {
        return values == null || values.isEmpty() ? "_待补充_" : String.join(", ", values);
    }

    private static String orPlaceholder(String value) {
        return value == null || value.isBlank() ? "_待补充_" : value;
    }

    /**
     * 卡片文件集合。
     *
     * @param readme    README.md 内容（人类说明）
     * @param assetYaml asset.yaml 内容（机器事实）
     */
    public record CardFiles(String readme, String assetYaml) {
    }
}
