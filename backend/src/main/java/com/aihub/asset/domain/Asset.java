/*
 * 功能: 资产聚合根，维护目录条目的不变量与生命周期行为。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 资产聚合根。
 *
 * <p>封装资产目录条目的不变量（坐标格式、类型与画像一致性、状态流转）与可变元数据的受控修改。
 * 领域层不依赖 Spring / MyBatis / 任何基础设施 SDK，由仓储适配器负责与持久化实体互转。
 */
public final class Asset {

    /** 命名空间与名称统一为小写字母、数字与连字符，便于映射为 Git 仓库名与 URL 友好别名。 */
    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{0,62}[a-z0-9]$");

    private final String assetId;
    private final AssetType type;
    private String organizationId;
    private String projectId;
    private final String namespace;
    private final String name;

    private String displayName;
    private String description;
    private Visibility visibility;
    private AssetStatus status;
    private List<String> owners;
    private List<String> tags;
    private List<String> tagIds;
    private String license;
    private String ownerTeamId;
    private List<String> aliases;
    private ModelProfile modelProfile;
    private DatasetProfile datasetProfile;
    private AssetRepositoryRef repository;
    private ProvisioningStatus provisioningStatus;
    private String deprecationReason;
    private String deprecationNote;
    private String replacementAssetId;
    private String sourceCommit;
    private String cardReadme;
    private String cardAssetYaml;

    private long rowVersion;
    private final String createdBy;
    private String updatedBy;
    private final Instant createdAt;
    private Instant updatedAt;

    private Asset(Builder builder) {
        this.assetId = builder.assetId;
        this.type = builder.type;
        this.organizationId = builder.organizationId;
        this.projectId = builder.projectId;
        this.namespace = builder.namespace;
        this.name = builder.name;
        this.displayName = builder.displayName;
        this.description = builder.description;
        this.visibility = builder.visibility;
        this.status = builder.status;
        this.owners = normalizeList(builder.owners);
        this.tags = normalizeList(builder.tags);
        this.tagIds = normalizeList(builder.tagIds);
        this.license = builder.license;
        this.ownerTeamId = builder.ownerTeamId;
        this.aliases = normalizeList(builder.aliases);
        this.modelProfile = builder.modelProfile;
        this.datasetProfile = builder.datasetProfile;
        this.repository = builder.repository;
        this.provisioningStatus = builder.provisioningStatus != null
                ? builder.provisioningStatus : ProvisioningStatus.NONE;
        this.deprecationReason = builder.deprecationReason;
        this.deprecationNote = builder.deprecationNote;
        this.replacementAssetId = builder.replacementAssetId;
        this.sourceCommit = builder.sourceCommit;
        this.cardReadme = builder.cardReadme;
        this.cardAssetYaml = builder.cardAssetYaml;
        this.rowVersion = builder.rowVersion;
        this.createdBy = builder.createdBy;
        this.updatedBy = builder.updatedBy;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    /**
     * 登记新资产，建立坐标不变量与类型/画像一致性，初始状态为 {@link AssetStatus#ACTIVE}。
     *
     * @param assetId        业务资产 ID
     * @param type           资产类型
     * @param organizationId 所属组织 ID
     * @param projectId      所属项目 ID
     * @param namespace      命名空间
     * @param name           名称
     * @param displayName    展示名称
     * @param description    描述
     * @param visibility     可见性
     * @param owners         Owner 列表
     * @param tags           标签列表
     * @param license        许可证
     * @param ownerTeamId    主 Owner 团队 ID
     * @param modelProfile   模型画像（模型类必填语义，可空字段）
     * @param datasetProfile 数据集画像（数据集类必填语义，可空字段）
     * @param createdBy      创建者主体 ID
     * @return 新建资产聚合
     */
    public static Asset create(String assetId,
                               AssetType type,
                               String organizationId,
                               String projectId,
                               String namespace,
                               String name,
                               String displayName,
                               String description,
                               Visibility visibility,
                               List<String> owners,
                               List<String> tags,
                               List<String> tagIds,
                               String license,
                               String ownerTeamId,
                               ModelProfile modelProfile,
                               DatasetProfile datasetProfile,
                               String createdBy) {
        requireSlug(namespace, "namespace");
        requireSlug(name, "name");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(visibility, "visibility must not be null");
        Instant now = Instant.now();
        Builder builder = new Builder()
                .assetId(assetId)
                .type(type)
                .organizationId(organizationId)
                .projectId(projectId)
                .namespace(namespace)
                .name(name)
                .displayName(displayName)
                .description(description)
                .visibility(visibility)
                .status(AssetStatus.ACTIVE)
                .owners(owners)
                .tags(tags)
                .tagIds(tagIds)
                .license(license)
                .ownerTeamId(ownerTeamId)
                .createdBy(createdBy)
                .updatedBy(createdBy)
                .createdAt(now)
                .updatedAt(now)
                .rowVersion(0L);
        applyProfiles(builder, type, modelProfile, datasetProfile);
        return new Asset(builder);
    }

    /**
     * 修改可变元数据。坐标（namespace/type/name）与创建审计不可变。
     */
    public void updateMetadata(String organizationId,
                               String projectId,
                               String displayName,
                               String description,
                               Visibility visibility,
                               List<String> owners,
                               List<String> tags,
                               List<String> tagIds,
                               String license,
                               ModelProfile modelProfile,
                               DatasetProfile datasetProfile,
                               String ownerTeamId,
                               String updatedBy) {
        this.organizationId = normalizeNullable(organizationId);
        this.projectId = normalizeNullable(projectId);
        this.displayName = displayName;
        this.description = description;
        if (visibility != null) {
            this.visibility = visibility;
        }
        if (owners != null) {
            this.owners = normalizeList(owners);
        }
        if (tags != null) {
            this.tags = normalizeList(tags);
        }
        if (tagIds != null) {
            this.tagIds = normalizeList(tagIds);
        }
        this.license = license;
        if (ownerTeamId != null) {
            this.ownerTeamId = ownerTeamId;
        }
        if (type == AssetType.MODEL && modelProfile != null) {
            this.modelProfile = modelProfile;
        }
        if (type == AssetType.DATASET && datasetProfile != null) {
            this.datasetProfile = datasetProfile;
        }
        touch(updatedBy);
    }

    /** 标记弃用：仍可访问但检索降权。仅 ACTIVE 可弃用。 */
    public void deprecate(String updatedBy) {
        deprecate(updatedBy, null, null, null);
    }

    /** 标记弃用（含原因与替代资产）。仅 ACTIVE 可弃用。 */
    public void deprecate(String updatedBy, String deprecationReason,
                          String deprecationNote, String replacementAssetId) {
        if (this.status != AssetStatus.ACTIVE) {
            throw new IllegalStateException(
                    "only ACTIVE assets can be deprecated, current: " + this.status);
        }
        this.status = AssetStatus.DEPRECATED;
        this.deprecationReason = deprecationReason;
        this.deprecationNote = deprecationNote;
        this.replacementAssetId = replacementAssetId;
        touch(updatedBy);
    }

    /** 重命名资产：保留旧坐标到 aliases。 */
    public void rename(String newNamespace, String newName, String updatedBy) {
        requireSlug(newNamespace, "namespace");
        requireSlug(newName, "name");
        if (newNamespace.equals(this.namespace) && newName.equals(this.name)) {
            return;
        }
        String oldAlias = this.namespace + "/" + this.type.name().toLowerCase() + "/" + this.name;
        if (this.aliases == null || !this.aliases.contains(oldAlias)) {
            List<String> updated = new java.util.ArrayList<>(this.aliases != null ? this.aliases : List.of());
            updated.add(oldAlias);
            this.aliases = List.copyOf(updated);
        }
        // 注意：namespace/name 坐标不变（V2 唯一约束），重命名仅修改 displayName + aliases
        // 真正的坐标重命名需要 asset_alias 表 + 异步重定向，P1 阶段仅记录别名
        this.displayName = newName;
        touch(updatedBy);
    }

    /** 归档：默认不返回。ACTIVE 或 DEPRECATED 可归档。 */
    public void archive(String updatedBy) {
        if (this.status != AssetStatus.ACTIVE && this.status != AssetStatus.DEPRECATED) {
            throw new IllegalStateException(
                    "only ACTIVE or DEPRECATED assets can be archived, current: " + this.status);
        }
        this.status = AssetStatus.ARCHIVED;
        touch(updatedBy);
    }

    /** 恢复：从 DEPRECATED 或 ARCHIVED 恢复为 ACTIVE。 */
    public void restore(String updatedBy) {
        if (this.status != AssetStatus.DEPRECATED && this.status != AssetStatus.ARCHIVED) {
            throw new IllegalStateException(
                    "only DEPRECATED or ARCHIVED assets can be restored, current: " + this.status);
        }
        this.status = AssetStatus.ACTIVE;
        touch(updatedBy);
    }

    /** 绑定已开通的 Git 仓库引用。 */
    public void attachRepository(AssetRepositoryRef ref) {
        this.repository = ref;
    }

    private void touch(String updatedBy) {
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    private static void applyProfiles(Builder builder,
                                      AssetType type,
                                      ModelProfile modelProfile,
                                      DatasetProfile datasetProfile) {
        if (type == AssetType.MODEL) {
            builder.modelProfile(modelProfile == null ? ModelProfile.empty() : modelProfile);
            builder.datasetProfile(null);
            return;
        }
        builder.datasetProfile(datasetProfile == null ? DatasetProfile.empty() : datasetProfile);
        builder.modelProfile(null);
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    private static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static void requireSlug(String value, String field) {
        if (value == null || !SLUG_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    field + " must be a lowercase slug of letters, digits and dashes: " + value);
        }
    }

    public String assetId() {
        return assetId;
    }

    public AssetType type() {
        return type;
    }

    public String organizationId() {
        return organizationId;
    }

    public String projectId() {
        return projectId;
    }

    public String namespace() {
        return namespace;
    }

    public String name() {
        return name;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public Visibility visibility() {
        return visibility;
    }

    public AssetStatus status() {
        return status;
    }

    public List<String> owners() {
        return owners;
    }

    public List<String> tags() {
        return tags;
    }

    public List<String> tagIds() {
        return tagIds;
    }

    public String license() {
        return license;
    }

    public String ownerTeamId() {
        return ownerTeamId;
    }

    public List<String> aliases() {
        return aliases;
    }

    public ModelProfile modelProfile() {
        return modelProfile;
    }

    public DatasetProfile datasetProfile() {
        return datasetProfile;
    }

    public AssetRepositoryRef repository() {
        return repository;
    }

    public ProvisioningStatus provisioningStatus() {
        return provisioningStatus;
    }

    public String deprecationReason() {
        return deprecationReason;
    }

    public String deprecationNote() {
        return deprecationNote;
    }

    public String replacementAssetId() {
        return replacementAssetId;
    }

    public String sourceCommit() {
        return sourceCommit;
    }

    public String cardReadme() {
        return cardReadme;
    }

    public String cardAssetYaml() {
        return cardAssetYaml;
    }

    /** 推进建仓状态（由 Saga/Job 调用）。 */
    public void advanceProvisioning(ProvisioningStatus status) {
        this.provisioningStatus = status;
    }

    public long rowVersion() {
        return rowVersion;
    }

    public String createdBy() {
        return createdBy;
    }

    public String updatedBy() {
        return updatedBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /**
     * 仓储水合用构建器；业务代码请使用 {@link #create} 工厂。
     */
    public static final class Builder {
        private String assetId;
        private AssetType type;
        private String organizationId;
        private String projectId;
        private String namespace;
        private String name;
        private String displayName;
        private String description;
        private Visibility visibility;
        private AssetStatus status;
        private List<String> owners;
        private List<String> tags;
        private List<String> tagIds;
        private String license;
        private String ownerTeamId;
        private List<String> aliases;
        private ModelProfile modelProfile;
        private DatasetProfile datasetProfile;
        private AssetRepositoryRef repository;
        private ProvisioningStatus provisioningStatus;
        private String deprecationReason;
        private String deprecationNote;
        private String replacementAssetId;
        private String sourceCommit;
        private String cardReadme;
        private String cardAssetYaml;
        private long rowVersion;
        private String createdBy;
        private String updatedBy;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder assetId(String assetId) {
            this.assetId = assetId;
            return this;
        }

        public Builder type(AssetType type) {
            this.type = type;
            return this;
        }

        public Builder organizationId(String organizationId) {
            this.organizationId = normalizeNullable(organizationId);
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = normalizeNullable(projectId);
            return this;
        }

        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder visibility(Visibility visibility) {
            this.visibility = visibility;
            return this;
        }

        public Builder status(AssetStatus status) {
            this.status = status;
            return this;
        }

        public Builder owners(List<String> owners) {
            this.owners = owners;
            return this;
        }

        public Builder tags(List<String> tags) {
            this.tags = tags;
            return this;
        }

        public Builder tagIds(List<String> tagIds) {
            this.tagIds = tagIds;
            return this;
        }

        public Builder license(String license) {
            this.license = license;
            return this;
        }

        public Builder ownerTeamId(String ownerTeamId) {
            this.ownerTeamId = ownerTeamId;
            return this;
        }

        public Builder aliases(List<String> aliases) {
            this.aliases = aliases;
            return this;
        }

        public Builder modelProfile(ModelProfile modelProfile) {
            this.modelProfile = modelProfile;
            return this;
        }

        public Builder datasetProfile(DatasetProfile datasetProfile) {
            this.datasetProfile = datasetProfile;
            return this;
        }

        public Builder repository(AssetRepositoryRef repository) {
            this.repository = repository;
            return this;
        }

        public Builder provisioningStatus(ProvisioningStatus provisioningStatus) {
            this.provisioningStatus = provisioningStatus;
            return this;
        }

        public Builder deprecationReason(String deprecationReason) {
            this.deprecationReason = deprecationReason;
            return this;
        }

        public Builder deprecationNote(String deprecationNote) {
            this.deprecationNote = deprecationNote;
            return this;
        }

        public Builder replacementAssetId(String replacementAssetId) {
            this.replacementAssetId = replacementAssetId;
            return this;
        }

        public Builder sourceCommit(String sourceCommit) {
            this.sourceCommit = sourceCommit;
            return this;
        }

        public Builder cardReadme(String cardReadme) {
            this.cardReadme = cardReadme;
            return this;
        }

        public Builder cardAssetYaml(String cardAssetYaml) {
            this.cardAssetYaml = cardAssetYaml;
            return this;
        }

        public Builder rowVersion(long rowVersion) {
            this.rowVersion = rowVersion;
            return this;
        }

        public Builder createdBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public Builder updatedBy(String updatedBy) {
            this.updatedBy = updatedBy;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Asset build() {
            return new Asset(this);
        }
    }
}
