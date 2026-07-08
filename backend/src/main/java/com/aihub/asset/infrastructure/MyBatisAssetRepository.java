/*
 * 功能: 资产仓储适配器，组合 MyBatis-Plus 单表 CRUD 与显式 SQL 检索实现领域端口。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.infrastructure;

import com.aihub.asset.domain.Asset;
import com.aihub.asset.domain.AssetRepository;
import com.aihub.asset.domain.AssetRepositoryRef;
import com.aihub.asset.domain.AssetSearchCriteria;
import com.aihub.asset.domain.AssetStatus;
import com.aihub.asset.domain.AssetSummary;
import com.aihub.asset.domain.AssetType;
import com.aihub.asset.domain.DatasetProfile;
import com.aihub.asset.domain.ModelProfile;
import com.aihub.asset.domain.ProvisioningStatus;
import com.aihub.asset.domain.Visibility;
import com.aihub.shared.api.CursorPage;
import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.ErrorCode;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/**
 * 资产仓储适配器。
 *
 * <p>单表 CRUD、坐标判重、逻辑删除与乐观锁使用 MyBatis-Plus；多表连接检索与权限下推委托
 * {@link AssetSearchDao} 的显式 SQL。负责领域聚合与持久化实体之间的互转，杜绝实体跨模块外泄。
 *
 * <p>受控标签 tagIds 经 {@code asset_tag} 关联表读写：insert 时批量插入关联，update 时先删后插重写关联，
 * findByAssetId 时查询关联回填 tagIds 到领域聚合。
 */
@Repository
public class MyBatisAssetRepository implements AssetRepository {

    /** UpdateWrapper.set 不经实体 typeHandler，故对 jsonb 列显式指定处理器，避免 JDBC 无法推断类型。 */
    private static final String JSONB_LIST_HANDLER =
            "typeHandler=com.aihub.asset.infrastructure.JsonbStringListTypeHandler";

    private final AssetMapper assetMapper;
    private final AssetModelMapper assetModelMapper;
    private final AssetDatasetMapper assetDatasetMapper;
    private final AssetTagMapper assetTagMapper;
    private final AssetSearchDao assetSearchDao;

    public MyBatisAssetRepository(AssetMapper assetMapper,
                                  AssetModelMapper assetModelMapper,
                                  AssetDatasetMapper assetDatasetMapper,
                                  AssetTagMapper assetTagMapper,
                                  AssetSearchDao assetSearchDao) {
        this.assetMapper = assetMapper;
        this.assetModelMapper = assetModelMapper;
        this.assetDatasetMapper = assetDatasetMapper;
        this.assetTagMapper = assetTagMapper;
        this.assetSearchDao = assetSearchDao;
    }

    @Override
    public Optional<Asset> findByAssetId(String assetId) {
        AssetEntity entity = assetMapper.selectOne(
                Wrappers.<AssetEntity>lambdaQuery().eq(AssetEntity::getAssetId, assetId));
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.of(toDomain(entity));
    }

    @Override
    public boolean existsByCoordinate(String namespace, AssetType type, String name) {
        Long count = assetMapper.selectCount(Wrappers.<AssetEntity>lambdaQuery()
                .eq(AssetEntity::getNamespace, namespace)
                .eq(AssetEntity::getType, type.name())
                .eq(AssetEntity::getName, name));
        return count != null && count > 0;
    }

    @Override
    public void insert(Asset asset) {
        try {
            assetMapper.insert(toEntity(asset));
            insertProfile(asset);
            insertTagAssociations(asset);
        } catch (DuplicateKeyException ex) {
            throw new ConflictException(
                    ErrorCode.ASSET_ALREADY_EXISTS,
                    "asset already exists: " + asset.namespace() + "/" + asset.type() + "/" + asset.name(),
                    Map.of("namespace", asset.namespace(), "type", asset.type().name(), "name", asset.name()));
        }
    }

    @Override
    public void update(Asset asset) {
        long currentVersion = asset.rowVersion();
        int affected = assetMapper.update(null, Wrappers.<AssetEntity>lambdaUpdate()
                .eq(AssetEntity::getAssetId, asset.assetId())
                .eq(AssetEntity::getRowVersion, currentVersion)
                .set(AssetEntity::getOrganizationId, asset.organizationId())
                .set(AssetEntity::getProjectId, asset.projectId())
                .set(AssetEntity::getDisplayName, asset.displayName())
                .set(AssetEntity::getDescription, asset.description())
                .set(AssetEntity::getVisibility, asset.visibility().name())
                .set(AssetEntity::getStatus, asset.status().name())
                .set(AssetEntity::getOwners, asset.owners(), JSONB_LIST_HANDLER)
                .set(AssetEntity::getTags, asset.tags(), JSONB_LIST_HANDLER)
                .set(AssetEntity::getLicense, asset.license())
                .set(AssetEntity::getOwnerTeamId, asset.ownerTeamId())
                .set(AssetEntity::getAliases, asset.aliases(), JSONB_LIST_HANDLER)
                .set(AssetEntity::getDeprecationReason, asset.deprecationReason())
                .set(AssetEntity::getDeprecationNote, asset.deprecationNote())
                .set(AssetEntity::getReplacementAssetId, asset.replacementAssetId())
                .set(AssetEntity::getRowVersion, currentVersion + 1)
                .set(AssetEntity::getUpdatedBy, asset.updatedBy())
                .set(AssetEntity::getUpdatedAt, asset.updatedAt())
                .set(asset.repository() != null, AssetEntity::getRepoFullName,
                        asset.repository() != null ? asset.repository().fullName() : null)
                .set(asset.repository() != null, AssetEntity::getRepoHtmlUrl,
                        asset.repository() != null ? asset.repository().htmlUrl() : null)
                .set(asset.repository() != null, AssetEntity::getRepoCloneUrl,
                        asset.repository() != null ? asset.repository().cloneUrl() : null)
                .set(AssetEntity::getProvisioningStatus,
                        asset.provisioningStatus() != null ? asset.provisioningStatus().name() : "NONE")
                .set(AssetEntity::getSourceCommit, asset.sourceCommit())
                .set(AssetEntity::getCardReadme, asset.cardReadme())
                .set(AssetEntity::getCardAssetYaml, asset.cardAssetYaml()));
        if (affected == 0) {
            throw new ConflictException(
                    ErrorCode.ASSET_CONCURRENT_MODIFICATION,
                    "asset was modified concurrently: " + asset.assetId());
        }
        updateProfile(asset);
        rewriteTagAssociations(asset);
    }

    @Override
    public void softDelete(String assetId, String updatedBy) {
        assetMapper.update(null, Wrappers.<AssetEntity>lambdaUpdate()
                .eq(AssetEntity::getAssetId, assetId)
                .set(AssetEntity::getDeleted, 1)
                .set(AssetEntity::getUpdatedBy, updatedBy)
                .set(AssetEntity::getUpdatedAt, Instant.now()));
    }

    @Override
    public CursorPage<AssetSummary> search(AssetSearchCriteria criteria) {
        return assetSearchDao.search(criteria);
    }

    @Override
    public Map<String, Map<String, Long>> facet(AssetSearchCriteria criteria) {
        return assetSearchDao.facet(criteria);
    }

    private void insertTagAssociations(Asset asset) {
        List<String> tagIds = asset.tagIds();
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        for (String tagId : tagIds) {
            AssetTagEntity tagEntity = new AssetTagEntity();
            tagEntity.setAssetId(asset.assetId());
            tagEntity.setTagId(tagId);
            tagEntity.setCreatedAt(Instant.now());
            assetTagMapper.insert(tagEntity);
        }
    }

    private void rewriteTagAssociations(Asset asset) {
        assetTagMapper.delete(Wrappers.<AssetTagEntity>lambdaQuery()
                .eq(AssetTagEntity::getAssetId, asset.assetId()));
        insertTagAssociations(asset);
    }

    private List<String> loadTagIds(String assetId) {
        List<AssetTagEntity> tags = assetTagMapper.selectList(Wrappers.<AssetTagEntity>lambdaQuery()
                .eq(AssetTagEntity::getAssetId, assetId));
        return tags.stream().map(AssetTagEntity::getTagId).toList();
    }

    private void insertProfile(Asset asset) {
        if (asset.type() == AssetType.MODEL) {
            ModelProfile profile = asset.modelProfile() == null ? ModelProfile.empty() : asset.modelProfile();
            AssetModelEntity entity = new AssetModelEntity();
            entity.setAssetId(asset.assetId());
            entity.setFramework(profile.framework());
            entity.setTask(profile.task());
            entity.setArchitecture(profile.architecture());
            entity.setParameterScale(profile.parameterScale());
            entity.setPrecision(profile.precision());
            entity.setWeightFormat(profile.weightFormat());
            entity.setRuntime(profile.runtime());
            entity.setKnownRisks(profile.knownRisks());
            entity.setUsageRestrictions(profile.usageRestrictions());
            entity.setSensitivityCode(profile.sensitivityCode());
            assetModelMapper.insert(entity);
            return;
        }
        DatasetProfile profile = asset.datasetProfile() == null ? DatasetProfile.empty() : asset.datasetProfile();
        AssetDatasetEntity entity = new AssetDatasetEntity();
        entity.setAssetId(asset.assetId());
        entity.setFormat(profile.format());
        entity.setModality(profile.modality());
        entity.setTaskCodes(profile.taskCodes());
        entity.setModalityCodes(profile.modalityCodes());
        entity.setFormatCodes(profile.formatCodes());
        entity.setLanguageCodes(profile.languageCodes());
        entity.setSensitivityCode(profile.sensitivityCode());
        entity.setSampleCount(profile.sampleCount());
        entity.setTotalBytes(profile.totalBytes());
        entity.setSizeBucketCode(profile.sizeBucketCode());
        assetDatasetMapper.insert(entity);
    }

    private void updateProfile(Asset asset) {
        if (asset.type() == AssetType.MODEL) {
            ModelProfile profile = asset.modelProfile() == null ? ModelProfile.empty() : asset.modelProfile();
            assetModelMapper.update(null, Wrappers.<AssetModelEntity>lambdaUpdate()
                    .eq(AssetModelEntity::getAssetId, asset.assetId())
                    .set(AssetModelEntity::getFramework, profile.framework())
                    .set(AssetModelEntity::getTask, profile.task())
                    .set(AssetModelEntity::getArchitecture, profile.architecture())
                    .set(AssetModelEntity::getParameterScale, profile.parameterScale())
                    .set(AssetModelEntity::getPrecision, profile.precision())
                    .set(AssetModelEntity::getWeightFormat, profile.weightFormat())
                    .set(AssetModelEntity::getRuntime, profile.runtime())
                    .set(AssetModelEntity::getKnownRisks, profile.knownRisks(), JSONB_LIST_HANDLER)
                    .set(AssetModelEntity::getUsageRestrictions, profile.usageRestrictions(), JSONB_LIST_HANDLER)
                    .set(AssetModelEntity::getSensitivityCode, profile.sensitivityCode()));
            return;
        }
        DatasetProfile profile = asset.datasetProfile() == null ? DatasetProfile.empty() : asset.datasetProfile();
        assetDatasetMapper.update(null, Wrappers.<AssetDatasetEntity>lambdaUpdate()
                .eq(AssetDatasetEntity::getAssetId, asset.assetId())
                .set(AssetDatasetEntity::getFormat, profile.format())
                .set(AssetDatasetEntity::getModality, profile.modality())
                .set(AssetDatasetEntity::getTaskCodes, profile.taskCodes(), JSONB_LIST_HANDLER)
                .set(AssetDatasetEntity::getModalityCodes, profile.modalityCodes(), JSONB_LIST_HANDLER)
                .set(AssetDatasetEntity::getFormatCodes, profile.formatCodes(), JSONB_LIST_HANDLER)
                .set(AssetDatasetEntity::getLanguageCodes, profile.languageCodes(), JSONB_LIST_HANDLER)
                .set(AssetDatasetEntity::getSensitivityCode, profile.sensitivityCode())
                .set(AssetDatasetEntity::getSampleCount, profile.sampleCount())
                .set(AssetDatasetEntity::getTotalBytes, profile.totalBytes())
                .set(AssetDatasetEntity::getSizeBucketCode, profile.sizeBucketCode()));
    }

    private AssetEntity toEntity(Asset asset) {
        AssetEntity entity = new AssetEntity();
        entity.setAssetId(asset.assetId());
        entity.setOrganizationId(asset.organizationId());
        entity.setProjectId(asset.projectId());
        entity.setType(asset.type().name());
        entity.setNamespace(asset.namespace());
        entity.setName(asset.name());
        entity.setDisplayName(asset.displayName());
        entity.setDescription(asset.description());
        entity.setVisibility(asset.visibility().name());
        entity.setStatus(asset.status().name());
        entity.setOwners(asset.owners());
        entity.setTags(asset.tags());
        entity.setLicense(asset.license());
        entity.setOwnerTeamId(asset.ownerTeamId());
        entity.setAliases(asset.aliases());
        AssetRepositoryRef ref = asset.repository();
        if (ref != null) {
            entity.setRepoFullName(ref.fullName());
            entity.setRepoHtmlUrl(ref.htmlUrl());
            entity.setRepoCloneUrl(ref.cloneUrl());
        }
        entity.setProvisioningStatus(
                asset.provisioningStatus() != null ? asset.provisioningStatus().name() : "NONE");
        entity.setSourceCommit(asset.sourceCommit());
        entity.setCardReadme(asset.cardReadme());
        entity.setCardAssetYaml(asset.cardAssetYaml());
        entity.setRowVersion(asset.rowVersion());
        entity.setCreatedBy(asset.createdBy());
        entity.setUpdatedBy(asset.updatedBy());
        entity.setCreatedAt(asset.createdAt());
        entity.setUpdatedAt(asset.updatedAt());
        entity.setDeprecationReason(asset.deprecationReason());
        entity.setDeprecationNote(asset.deprecationNote());
        entity.setReplacementAssetId(asset.replacementAssetId());
        entity.setDeleted(0);
        return entity;
    }

    private Asset toDomain(AssetEntity entity) {
        AssetType type = AssetType.valueOf(entity.getType());
        List<String> tagIds = loadTagIds(entity.getAssetId());
        Asset.Builder builder = new Asset.Builder()
                .assetId(entity.getAssetId())
                .organizationId(entity.getOrganizationId())
                .projectId(entity.getProjectId())
                .type(type)
                .namespace(entity.getNamespace())
                .name(entity.getName())
                .displayName(entity.getDisplayName())
                .description(entity.getDescription())
                .visibility(Visibility.valueOf(entity.getVisibility()))
                .status(AssetStatus.valueOf(entity.getStatus()))
                .owners(entity.getOwners())
                .tags(entity.getTags())
                .tagIds(tagIds)
                .license(entity.getLicense())
                .ownerTeamId(entity.getOwnerTeamId())
                .aliases(entity.getAliases())
                .rowVersion(entity.getRowVersion() == null ? 0L : entity.getRowVersion())
                .createdBy(entity.getCreatedBy())
                .updatedBy(entity.getUpdatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt());
        if (entity.getProvisioningStatus() != null) {
            builder.provisioningStatus(ProvisioningStatus.valueOf(entity.getProvisioningStatus()));
        }
        builder.sourceCommit(entity.getSourceCommit());
        builder.cardReadme(entity.getCardReadme());
        builder.cardAssetYaml(entity.getCardAssetYaml());
        builder.deprecationReason(entity.getDeprecationReason());
        builder.deprecationNote(entity.getDeprecationNote());
        builder.replacementAssetId(entity.getReplacementAssetId());
        if (entity.getRepoFullName() != null) {
            builder.repository(new AssetRepositoryRef(
                    entity.getRepoFullName(), entity.getRepoHtmlUrl(), entity.getRepoCloneUrl()));
        }
        attachProfile(builder, type, entity.getAssetId());
        return builder.build();
    }

    private void attachProfile(Asset.Builder builder, AssetType type, String assetId) {
        if (type == AssetType.MODEL) {
            AssetModelEntity model = assetModelMapper.selectById(assetId);
            builder.modelProfile(model == null
                    ? ModelProfile.empty()
                    : new ModelProfile(model.getFramework(), model.getTask(), model.getArchitecture(),
                            model.getParameterScale(), model.getPrecision(), model.getWeightFormat(),
                            model.getRuntime(), model.getKnownRisks(), model.getUsageRestrictions(),
                            model.getSensitivityCode()));
            return;
        }
        AssetDatasetEntity dataset = assetDatasetMapper.selectById(assetId);
        builder.datasetProfile(dataset == null
                ? DatasetProfile.empty()
                : new DatasetProfile(dataset.getFormat(), dataset.getModality(),
                        dataset.getTaskCodes(), dataset.getModalityCodes(), dataset.getFormatCodes(),
                        dataset.getLanguageCodes(), dataset.getSensitivityCode(),
                        dataset.getSampleCount(), dataset.getTotalBytes(), dataset.getSizeBucketCode()));
    }
}
