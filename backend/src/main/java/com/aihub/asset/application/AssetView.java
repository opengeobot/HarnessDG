/*
 * 功能: 资产详情视图，作为对外返回的资产完整表示（非持久化实体）。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.application;

import com.aihub.asset.domain.Asset;
import com.aihub.asset.domain.AssetRepositoryRef;
import com.aihub.asset.domain.AssetStatus;
import com.aihub.asset.domain.AssetType;
import com.aihub.asset.domain.DatasetProfile;
import com.aihub.asset.domain.ModelProfile;
import com.aihub.asset.domain.ProvisioningStatus;
import com.aihub.asset.domain.Visibility;
import java.time.Instant;
import java.util.List;

/**
 * 资产详情视图。
 *
 * <p>对外只暴露业务字段，绝不返回持久化实体。模型/数据集画像按类型择一返回，另一为 {@code null}。
 *
 * @param assetId        业务资产 ID
 * @param type           资产类型
 * @param namespace      命名空间
 * @param organizationId 组织 ID（治理作用域）
 * @param projectId      项目 ID（治理作用域）
 * @param name           名称
 * @param displayName    展示名称
 * @param description    描述
 * @param visibility     可见性
 * @param status         状态
 * @param owners         Owner 列表
 * @param tags           标签列表（legacy 自由标签，只读回显）
 * @param tagIds         受控标签 ID 列表（来自 asset_tag 关联）
 * @param license        许可证
 * @param model          模型画像（仅模型类非空）
 * @param dataset        数据集画像（仅数据集类非空）
 * @param repository          仓库引用
 * @param provisioningStatus  建仓异步状态
 * @param card                Card 投影（untrustedContent，前端需 DOMPurify 渲染）
 * @param createdAt           创建时间
 * @param updatedAt           更新时间
 */
public record AssetView(String assetId,
                        AssetType type,
                        String namespace,
                        String organizationId,
                        String projectId,
                        String name,
                        String displayName,
                        String description,
                        Visibility visibility,
                        AssetStatus status,
                        List<String> owners,
                        List<String> tags,
                        List<String> tagIds,
                        String license,
                        ModelView model,
                        DatasetView dataset,
                        RepositoryView repository,
                        ProvisioningStatus provisioningStatus,
                        CardView card,
                        Instant createdAt,
                        Instant updatedAt) {

    /** 模型画像视图。 */
    public record ModelView(String framework, String task, String architecture) {
    }

    /** 数据集画像视图。 */
    public record DatasetView(String format, String modality,
                              List<String> taskCodes, List<String> modalityCodes,
                              List<String> formatCodes, List<String> languageCodes,
                              String sensitivityCode, Long sampleCount,
                              Long totalBytes, String sizeBucketCode) {
    }

    /** 仓库引用视图。 */
    public record RepositoryView(String fullName, String htmlUrl, String cloneUrl) {
    }

    /** Card 投影视图（untrustedContent 标记前端需 DOMPurify 清洗）。 */
    public record CardView(String readme, String assetYaml, String sourceCommit,
                           boolean untrustedContent) {
    }

    /**
     * 由资产聚合构造视图。
     *
     * @param asset 资产聚合
     * @return 资产详情视图
     */
    public static AssetView from(Asset asset) {
        return new AssetView(
                asset.assetId(),
                asset.type(),
                asset.namespace(),
                asset.organizationId(),
                asset.projectId(),
                asset.name(),
                asset.displayName(),
                asset.description(),
                asset.visibility(),
                asset.status(),
                asset.owners(),
                asset.tags(),
                asset.tagIds(),
                asset.license(),
                modelView(asset.modelProfile()),
                datasetView(asset.datasetProfile()),
                repositoryView(asset.repository()),
                asset.provisioningStatus(),
                cardView(asset),
                asset.createdAt(),
                asset.updatedAt());
    }

    private static ModelView modelView(ModelProfile profile) {
        return profile == null ? null
                : new ModelView(profile.framework(), profile.task(), profile.architecture());
    }

    private static DatasetView datasetView(DatasetProfile profile) {
        return profile == null ? null
                : new DatasetView(profile.format(), profile.modality(),
                        profile.taskCodes(), profile.modalityCodes(),
                        profile.formatCodes(), profile.languageCodes(),
                        profile.sensitivityCode(), profile.sampleCount(),
                        profile.totalBytes(), profile.sizeBucketCode());
    }

    private static RepositoryView repositoryView(AssetRepositoryRef ref) {
        return ref == null ? null : new RepositoryView(ref.fullName(), ref.htmlUrl(), ref.cloneUrl());
    }

    private static CardView cardView(Asset asset) {
        if (asset.cardReadme() == null && asset.cardAssetYaml() == null) {
            return null;
        }
        return new CardView(asset.cardReadme(), asset.cardAssetYaml(),
                asset.sourceCommit(), true);
    }
}
