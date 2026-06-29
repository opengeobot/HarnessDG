/*
 * 功能: 资产应用服务，编排资产登记、查询、更新、删除与检索用例。
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
import com.aihub.asset.domain.Visibility;
import com.aihub.shared.api.CursorPage;
import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 资产应用服务。
 *
 * <p>编排资产目录用例，复用领域不变量、仓储端口与仓库开通端口。仅返回视图对象，不泄露持久化实体。
 * 资源不存在与无权访问对外统一返回 {@code ASSET_NOT_FOUND}，避免私有资产被枚举。
 */
@Service
public class AssetApplicationService {

    private final AssetRepository assetRepository;
    private final AssetRepositoryProvisioner repositoryProvisioner;
    private final AssetAccessPolicy accessPolicy;
    private final IdGenerator idGenerator;

    public AssetApplicationService(AssetRepository assetRepository,
                                   AssetRepositoryProvisioner repositoryProvisioner,
                                   AssetAccessPolicy accessPolicy,
                                   IdGenerator idGenerator) {
        this.assetRepository = assetRepository;
        this.repositoryProvisioner = repositoryProvisioner;
        this.accessPolicy = accessPolicy;
        this.idGenerator = idGenerator;
    }

    /**
     * 登记新资产：校验坐标唯一、开通 Git 仓库并写入初始卡片、持久化目录条目。
     *
     * <p>P1 采用“先开通仓库再落库”的简化顺序，确保落库的资产始终带有仓库引用；
     * 跨系统的强一致开通（Saga/Outbox/对账）属 P2 范围。
     */
    @Transactional
    public AssetView createAsset(CreateAssetCommand command) {
        if (command.type() == null) {
            throw new ValidationException("asset type is required");
        }
        if (command.visibility() == null) {
            throw new ValidationException("asset visibility is required");
        }
        Asset asset = buildNewAsset(command);
        if (assetRepository.existsByCoordinate(asset.namespace(), asset.type(), asset.name())) {
            throw coordinateConflict(asset.namespace(), asset.type(), asset.name());
        }
        AssetCard.CardFiles card = AssetCard.render(asset);
        AssetRepositoryRef ref = repositoryProvisioner.provision(new AssetRepositoryProvisioner.ProvisionRequest(
                asset.namespace(), asset.name(), asset.type(), asset.description(),
                asset.visibility(), card.readme(), card.assetYaml()));
        asset.attachRepository(ref);
        assetRepository.insert(asset);
        return AssetView.from(asset);
    }

    /**
     * 查询资产详情。
     *
     * @param assetId     业务资产 ID
     * @param principalId 主体 ID
     * @return 资产详情视图
     */
    @Transactional(readOnly = true)
    public AssetView getAsset(String assetId, String principalId) {
        Asset asset = loadAccessible(assetId, principalId);
        return AssetView.from(asset);
    }

    /**
     * 更新资产可变元数据（乐观锁）。
     */
    @Transactional
    public AssetView updateAsset(String assetId, UpdateAssetCommand command) {
        Asset asset = loadAccessible(assetId, command.principalId());
        asset.updateMetadata(
                command.displayName(),
                command.description(),
                command.visibility(),
                command.owners(),
                command.tags(),
                command.license(),
                command.model(),
                command.dataset(),
                command.principalId());
        assetRepository.update(asset);
        return AssetView.from(asset);
    }

    /**
     * 逻辑删除资产。
     */
    @Transactional
    public void deleteAsset(String assetId, String principalId) {
        Asset asset = loadAccessible(assetId, principalId);
        assetRepository.softDelete(asset.assetId(), principalId);
    }

    /**
     * 检索资产摘要，权限可见性在数据库阶段过滤。
     */
    @Transactional(readOnly = true)
    public CursorPage<AssetSummaryView> searchAssets(AssetSearchQuery query) {
        Set<Visibility> allowed = accessPolicy.visibleVisibilities(query.principalId());
        Set<AssetStatus> statuses = query.includeArchived()
                ? Set.of(AssetStatus.ACTIVE, AssetStatus.DEPRECATED, AssetStatus.ARCHIVED)
                : Set.of(AssetStatus.ACTIVE, AssetStatus.DEPRECATED);
        AssetSearchCriteria criteria = new AssetSearchCriteria(
                query.keyword(), query.type(), query.namespace(), query.framework(), query.task(),
                query.format(), query.modality(), query.tag(), query.owner(),
                statuses, allowed, query.cursor(), query.limit());
        CursorPage<com.aihub.asset.domain.AssetSummary> page = assetRepository.search(criteria);
        return new CursorPage<>(
                page.items().stream().map(AssetSummaryView::from).toList(),
                page.nextCursor(),
                page.hasMore());
    }

    private Asset buildNewAsset(CreateAssetCommand command) {
        String assetId = idGenerator.generate(IdPrefix.ASSET);
        try {
            return Asset.create(
                    assetId,
                    command.type(),
                    command.namespace(),
                    command.name(),
                    command.displayName(),
                    command.description(),
                    command.visibility(),
                    command.owners(),
                    command.tags(),
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
                .orElseThrow(() -> new NotFoundException("asset not found: " + assetId));
        return asset;
    }

    private ConflictException coordinateConflict(String namespace, AssetType type, String name) {
        return new ConflictException(
                ErrorCode.ASSET_ALREADY_EXISTS,
                "asset already exists: " + namespace + "/" + type + "/" + name,
                Map.of("namespace", namespace, "type", type.name(), "name", name));
    }
}
