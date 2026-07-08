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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {};

    private static final String FIND_BY_ID_JOIN_SQL = """
            SELECT a.id, a.asset_id, a.type, a.namespace, a.organization_id, a.project_id, a.name,
                   a.display_name, a.description, a.visibility, a.status, a.license,
                   a.owners::text AS owners_json, a.tags::text AS tags_json,
                   a.owner_team_id, a.aliases::text AS aliases_json,
                   a.provisioning_status, a.source_commit, a.card_readme, a.card_asset_yaml,
                   a.deprecation_reason, a.deprecation_note, a.replacement_asset_id,
                   a.repo_full_name, a.repo_html_url, a.repo_clone_url,
                   a.row_version, a.created_by, a.updated_by, a.created_at, a.updated_at,
                   am.framework, am.task, am.architecture, am.parameter_scale, am.precision,
                   am.weight_format, am.runtime, am.known_risks::text AS m_known_risks_json,
                   am.usage_restrictions::text AS m_usage_restrictions_json, am.sensitivity_code AS m_sensitivity,
                   ad.format, ad.modality, ad.task_codes::text AS d_task_codes_json,
                   ad.modality_codes::text AS d_modality_codes_json, ad.format_codes::text AS d_format_codes_json,
                   ad.language_codes::text AS d_language_codes_json, ad.sensitivity_code AS d_sensitivity,
                   ad.sample_count, ad.total_bytes, ad.size_bucket_code,
                   at2.tag_id AS tag_id
            FROM asset a
            LEFT JOIN asset_model am ON am.asset_id = a.asset_id
            LEFT JOIN asset_dataset ad ON ad.asset_id = a.asset_id
            LEFT JOIN asset_tag at2 ON at2.asset_id = a.asset_id
            WHERE a.asset_id = :assetId AND a.deleted = 0
            """;

    private final AssetMapper assetMapper;
    private final AssetModelMapper assetModelMapper;
    private final AssetDatasetMapper assetDatasetMapper;
    private final AssetTagMapper assetTagMapper;
    private final AssetSearchDao assetSearchDao;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public MyBatisAssetRepository(AssetMapper assetMapper,
                                  AssetModelMapper assetModelMapper,
                                  AssetDatasetMapper assetDatasetMapper,
                                  AssetTagMapper assetTagMapper,
                                  AssetSearchDao assetSearchDao,
                                  NamedParameterJdbcTemplate jdbcTemplate) {
        this.assetMapper = assetMapper;
        this.assetModelMapper = assetModelMapper;
        this.assetDatasetMapper = assetDatasetMapper;
        this.assetTagMapper = assetTagMapper;
        this.assetSearchDao = assetSearchDao;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Asset> findByAssetId(String assetId) {
        MapSqlParameterSource params = new MapSqlParameterSource("assetId", assetId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(FIND_BY_ID_JOIN_SQL, params);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(buildDomainFromJoin(rows));
    }

    private Asset buildDomainFromJoin(List<Map<String, Object>> rows) {
        Map<String, Object> first = rows.get(0);
        AssetType type = AssetType.valueOf((String) first.get("type"));
        List<String> tagIds = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String tagId = (String) row.get("tag_id");
            if (tagId != null) {
                tagIds.add(tagId);
            }
        }
        Asset.Builder builder = new Asset.Builder()
                .assetId((String) first.get("asset_id"))
                .organizationId((String) first.get("organization_id"))
                .projectId((String) first.get("project_id"))
                .type(type)
                .namespace((String) first.get("namespace"))
                .name((String) first.get("name"))
                .displayName((String) first.get("display_name"))
                .description((String) first.get("description"))
                .visibility(Visibility.valueOf((String) first.get("visibility")))
                .status(AssetStatus.valueOf((String) first.get("status")))
                .owners(parseJsonList((String) first.get("owners_json")))
                .tags(parseJsonList((String) first.get("tags_json")))
                .tagIds(tagIds)
                .license((String) first.get("license"))
                .ownerTeamId((String) first.get("owner_team_id"))
                .aliases(parseJsonList((String) first.get("aliases_json")))
                .rowVersion(first.get("row_version") == null ? 0L : ((Number) first.get("row_version")).longValue())
                .createdBy((String) first.get("created_by"))
                .updatedBy((String) first.get("updated_by"))
                .createdAt(toInstant(first.get("created_at")))
                .updatedAt(toInstant(first.get("updated_at")));
        String provStatus = (String) first.get("provisioning_status");
        if (provStatus != null) {
            builder.provisioningStatus(ProvisioningStatus.valueOf(provStatus));
        }
        builder.sourceCommit((String) first.get("source_commit"));
        builder.cardReadme((String) first.get("card_readme"));
        builder.cardAssetYaml((String) first.get("card_asset_yaml"));
        builder.deprecationReason((String) first.get("deprecation_reason"));
        builder.deprecationNote((String) first.get("deprecation_note"));
        builder.replacementAssetId((String) first.get("replacement_asset_id"));
        String repoFullName = (String) first.get("repo_full_name");
        if (repoFullName != null) {
            builder.repository(new AssetRepositoryRef(
                    repoFullName, (String) first.get("repo_html_url"), (String) first.get("repo_clone_url")));
        }
        if (type == AssetType.MODEL) {
            builder.modelProfile(new ModelProfile(
                    (String) first.get("framework"), (String) first.get("task"),
                    (String) first.get("architecture"), (String) first.get("parameter_scale"),
                    (String) first.get("precision"), (String) first.get("weight_format"),
                    (String) first.get("runtime"),
                    parseJsonList((String) first.get("m_known_risks_json")),
                    parseJsonList((String) first.get("m_usage_restrictions_json")),
                    (String) first.get("m_sensitivity")));
        } else {
            builder.datasetProfile(new DatasetProfile(
                    (String) first.get("format"), (String) first.get("modality"),
                    parseJsonList((String) first.get("d_task_codes_json")),
                    parseJsonList((String) first.get("d_modality_codes_json")),
                    parseJsonList((String) first.get("d_format_codes_json")),
                    parseJsonList((String) first.get("d_language_codes_json")),
                    (String) first.get("d_sensitivity"),
                    toLong(first.get("sample_count")), toLong(first.get("total_bytes")),
                    (String) first.get("size_bucket_code")));
        }
        return builder.build();
    }

    private static List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, LIST_TYPE);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static Instant toInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant i) return i;
        if (value instanceof OffsetDateTime odt) return odt.toInstant();
        return null;
    }

    private static Long toLong(Object value) {
        if (value == null) return null;
        return ((Number) value).longValue();
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

    @Override
    public void insertAlias(String assetId, String oldNamespace, String oldName) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO asset_alias (asset_id, old_namespace, old_name) VALUES (:assetId, :ns, :name)",
                    new MapSqlParameterSource()
                            .addValue("assetId", assetId)
                            .addValue("ns", oldNamespace)
                            .addValue("name", oldName));
        } catch (DuplicateKeyException ignored) {
            // 幂等：同一旧坐标已记录则跳过
        }
    }

    @Override
    public Optional<String> findByAlias(String namespace, String name) {
        List<String> ids = jdbcTemplate.queryForList(
                "SELECT asset_id FROM asset_alias WHERE old_namespace = :ns AND old_name = :name LIMIT 1",
                new MapSqlParameterSource("ns", namespace).addValue("name", name),
                String.class);
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
    }
}
