/*
 * 功能: 资产应用服务，编排资产登记、查询、更新、删除与检索用例，接入授权/审计/字典/标签治理。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.application;

import com.aihub.asset.domain.Asset;
import com.aihub.asset.domain.AssetCard;
import com.aihub.asset.domain.AssetRepository;
import com.aihub.asset.domain.AssetRepositoryProvisioner;
import com.aihub.asset.domain.AssetRepositoryRef;
import com.aihub.asset.domain.AssetSearchCriteria;
import com.aihub.asset.domain.AssetStatus;
import com.aihub.asset.domain.AssetType;
import com.aihub.asset.domain.DatasetProfile;
import com.aihub.asset.domain.ModelProfile;
import com.aihub.asset.domain.Visibility;
import com.aihub.audit.application.AuditEvent;
import com.aihub.audit.application.AuditService;
import com.aihub.audit.domain.AuditResult;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.shared.api.CursorPage;
import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.taxonomy.dictionary.application.DictionaryValidationPort;
import com.aihub.taxonomy.tag.application.TagDtos.TagScopeContext;
import com.aihub.taxonomy.tag.application.TagValidationService;
import com.aihub.taxonomy.tag.domain.TagScopeType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 资产应用服务。
 *
 * <p>编排资产目录用例，复用领域不变量、仓储端口与仓库开通端口。仅返回视图对象，不泄露持久化实体。
 * 资源不存在与无权访问对外统一返回 {@code ASSET_NOT_FOUND}，避免私有资产被枚举。
 *
 * <p>接入平台治理能力：
 * <ul>
 *   <li>授权：create/update/delete 需 {@code asset:manage}，read/search 需 {@code asset:read}。</li>
 *   <li>字典校验：license/framework/task/format/modality 按 asset type 语义校验 ACTIVE 字典项。</li>
 *   <li>标签校验：tagIds 经 {@link TagValidationService#resolveActiveTags} 校验全部存在且 ACTIVE。</li>
 *   <li>审计：写操作记录 ASSET_CREATED/ASSET_UPDATED/ASSET_DELETED；审计失败不阻塞主流程。</li>
 * </ul>
 */
@Service
public class AssetApplicationService {

    private static final Logger LOG = LoggerFactory.getLogger(AssetApplicationService.class);

    private static final String DICT_LICENSE = "license_catalog";
    private static final String DICT_FRAMEWORK = "model_framework";
    private static final String DICT_TASK = "model_task";
    private static final String DICT_FORMAT = "dataset_format";
    private static final String DICT_MODALITY = "dataset_modality";

    private final AssetRepository assetRepository;
    private final AssetRepositoryProvisioner repositoryProvisioner;
    private final AssetAccessPolicy accessPolicy;
    private final IdGenerator idGenerator;
    private final AuthorizationService authorizationService;
    private final DictionaryValidationPort dictionaryValidationPort;
    private final TagValidationService tagValidationService;
    private final AuditService auditService;

    public AssetApplicationService(AssetRepository assetRepository,
                                   AssetRepositoryProvisioner repositoryProvisioner,
                                   AssetAccessPolicy accessPolicy,
                                   IdGenerator idGenerator,
                                   AuthorizationService authorizationService,
                                   DictionaryValidationPort dictionaryValidationPort,
                                   TagValidationService tagValidationService,
                                   AuditService auditService) {
        this.assetRepository = assetRepository;
        this.repositoryProvisioner = repositoryProvisioner;
        this.accessPolicy = accessPolicy;
        this.idGenerator = idGenerator;
        this.authorizationService = authorizationService;
        this.dictionaryValidationPort = dictionaryValidationPort;
        this.tagValidationService = tagValidationService;
        this.auditService = auditService;
    }

    /**
     * 登记新资产：校验字典治理字段与受控标签、授权、开通 Git 仓库并写入初始卡片、持久化目录条目、审计。
     */
    @Transactional
    public AssetView createAsset(CreateAssetCommand command) {
        if (command.type() == null) {
            throw new ValidationException("asset type is required");
        }
        if (command.visibility() == null) {
            throw new ValidationException("asset visibility is required");
        }
        authorizationService.requirePermission(Permissions.ASSET_MANAGE);
        validateGovernanceFields(command.type(), command.license(),
                modelFramework(command), modelTask(command),
                datasetFormat(command), datasetModality(command));
        List<String> validatedTagIds = resolveValidatedTagIds(command.tagIds(), command.organizationId());
        Asset asset = buildNewAsset(command, validatedTagIds);
        if (assetRepository.existsByCoordinate(asset.namespace(), asset.type(), asset.name())) {
            throw coordinateConflict(asset.namespace(), asset.type(), asset.name());
        }
        AssetCard.CardFiles card = AssetCard.render(asset);
        AssetRepositoryRef ref = repositoryProvisioner.provision(new AssetRepositoryProvisioner.ProvisionRequest(
                asset.namespace(), asset.name(), asset.type(), asset.description(),
                asset.visibility(), card.readme(), card.assetYaml()));
        asset.attachRepository(ref);
        assetRepository.insert(asset);
        auditAsset("ASSET_CREATED", command.principalId(), asset.assetId(), Map.of(
                "namespace", asset.namespace(), "name", asset.name(), "type", asset.type().name()));
        return AssetView.from(asset);
    }

    /**
     * 查询资产详情（防枚举：资源不存在与无权访问统一返回 NotFound）。
     */
    @Transactional(readOnly = true)
    public AssetView getAsset(String assetId, String principalId) {
        authorizationService.requirePermission(Permissions.ASSET_READ);
        Asset asset = loadAccessible(assetId, principalId);
        return AssetView.from(asset);
    }

    /**
     * 更新资产可变元数据（乐观锁、字典校验、标签校验、审计）。
     */
    @Transactional
    public AssetView updateAsset(String assetId, UpdateAssetCommand command) {
        authorizationService.requirePermission(Permissions.ASSET_MANAGE);
        Asset asset = loadAccessible(assetId, command.principalId());
        validateGovernanceFields(asset.type(), command.license(),
                modelFramework(command), modelTask(command),
                datasetFormat(command), datasetModality(command));
        List<String> validatedTagIds = resolveValidatedTagIds(command.tagIds(),
                command.organizationId() != null ? command.organizationId() : asset.organizationId());
        asset.updateMetadata(
                command.organizationId(),
                command.projectId(),
                command.displayName(),
                command.description(),
                command.visibility(),
                command.owners(),
                command.tags(),
                validatedTagIds,
                command.license(),
                command.model(),
                command.dataset(),
                command.principalId());
        assetRepository.update(asset);
        auditAsset("ASSET_UPDATED", command.principalId(), asset.assetId(), Map.of(
                "namespace", asset.namespace(), "name", asset.name()));
        return AssetView.from(asset);
    }

    /**
     * 逻辑删除资产（授权、审计）。
     */
    @Transactional
    public void deleteAsset(String assetId, String principalId) {
        authorizationService.requirePermission(Permissions.ASSET_MANAGE);
        Asset asset = loadAccessible(assetId, principalId);
        assetRepository.softDelete(asset.assetId(), principalId);
        auditAsset("ASSET_DELETED", principalId, asset.assetId(), Map.of(
                "namespace", asset.namespace(), "name", asset.name()));
    }

    /**
     * 检索资产摘要，权限可见性在数据库阶段过滤。
     */
    @Transactional(readOnly = true)
    public CursorPage<AssetSummaryView> searchAssets(AssetSearchQuery query) {
        authorizationService.requirePermission(Permissions.ASSET_READ);
        Set<Visibility> allowed = accessPolicy.visibleVisibilities(query.principalId());
        Set<AssetStatus> statuses = query.includeArchived()
                ? Set.of(AssetStatus.ACTIVE, AssetStatus.DEPRECATED, AssetStatus.ARCHIVED)
                : Set.of(AssetStatus.ACTIVE, AssetStatus.DEPRECATED);
        AssetSearchCriteria criteria = new AssetSearchCriteria(
                query.keyword(), query.type(), query.namespace(), query.organizationId(),
                query.framework(), query.task(), query.format(), query.modality(),
                query.tagId(), query.owner(), statuses, allowed, query.cursor(), query.limit());
        CursorPage<com.aihub.asset.domain.AssetSummary> page = assetRepository.search(criteria);
        return new CursorPage<>(
                page.items().stream().map(AssetSummaryView::from).toList(),
                page.nextCursor(),
                page.hasMore());
    }

    private Asset buildNewAsset(CreateAssetCommand command, List<String> tagIds) {
        String assetId = idGenerator.generate(IdPrefix.ASSET);
        try {
            return Asset.create(
                    assetId,
                    command.type(),
                    command.organizationId(),
                    command.projectId(),
                    command.namespace(),
                    command.name(),
                    command.displayName(),
                    command.description(),
                    command.visibility(),
                    command.owners(),
                    command.tags(),
                    tagIds,
                    command.license(),
                    command.model(),
                    command.dataset(),
                    command.principalId());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(ex.getMessage());
        }
    }

    private Asset loadAccessible(String assetId, String principalId) {
        Asset asset = assetRepository.findByAssetId(assetId)
                .filter(found -> accessPolicy.canAccess(found, principalId))
                .orElseThrow(() -> new NotFoundException(ErrorCode.ASSET_NOT_FOUND,
                        "asset not found: " + assetId, Map.of()));
        return asset;
    }

    private void validateGovernanceFields(AssetType type, String license,
                                          String framework, String task,
                                          String format, String modality) {
        validateDictItem(DICT_LICENSE, license);
        if (type == AssetType.MODEL) {
            validateDictItem(DICT_FRAMEWORK, framework);
            validateDictItem(DICT_TASK, task);
            return;
        }
        validateDictItem(DICT_FORMAT, format);
        validateDictItem(DICT_MODALITY, modality);
    }

    private void validateDictItem(String dictCode, String itemCode) {
        if (!StringUtils.hasText(itemCode)) {
            return;
        }
        dictionaryValidationPort.validateItemCode(dictCode, itemCode.trim());
    }

    private List<String> resolveValidatedTagIds(List<String> tagIds, String organizationId) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        TagScopeContext scopeContext = StringUtils.hasText(organizationId)
                ? new TagScopeContext(TagScopeType.ORGANIZATION, organizationId.trim())
                : TagScopeContext.platform();
        return tagValidationService.resolveActiveTags(tagIds, scopeContext)
                .stream().map(t -> t.tagId()).toList();
    }

    private void auditAsset(String eventType, String principalId, String assetId,
                           Map<String, Object> attributes) {
        try {
            AuditEvent event = new AuditEvent(
                    eventType, eventType, principalId, null,
                    "ASSET", assetId, null, null,
                    AuditResult.SUCCEEDED, null, attributes);
            auditService.record(event);
        } catch (Exception ex) {
            LOG.error("failed to record audit event eventType={} assetId={}", eventType, assetId, ex);
        }
    }

    private ConflictException coordinateConflict(String namespace, AssetType type, String name) {
        return new ConflictException(
                ErrorCode.ASSET_ALREADY_EXISTS,
                "asset already exists: " + namespace + "/" + type + "/" + name,
                Map.of("namespace", namespace, "type", type.name(), "name", name));
    }

    private static String modelFramework(CreateAssetCommand command) {
        return command.model() == null ? null : command.model().framework();
    }

    private static String modelTask(CreateAssetCommand command) {
        return command.model() == null ? null : command.model().task();
    }

    private static String datasetFormat(CreateAssetCommand command) {
        return command.dataset() == null ? null : command.dataset().format();
    }

    private static String datasetModality(CreateAssetCommand command) {
        return command.dataset() == null ? null : command.dataset().modality();
    }

    private static String modelFramework(UpdateAssetCommand command) {
        return command.model() == null ? null : command.model().framework();
    }

    private static String modelTask(UpdateAssetCommand command) {
        return command.model() == null ? null : command.model().task();
    }

    private static String datasetFormat(UpdateAssetCommand command) {
        return command.dataset() == null ? null : command.dataset().format();
    }

    private static String datasetModality(UpdateAssetCommand command) {
        return command.dataset() == null ? null : command.dataset().modality();
    }
}
