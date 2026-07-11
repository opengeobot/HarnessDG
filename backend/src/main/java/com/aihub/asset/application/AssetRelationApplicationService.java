/*
 * 功能: 资产血缘关系写服务——创建 parent→child 关系边并审计。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.asset.application;

import com.aihub.asset.domain.Asset;
import com.aihub.asset.domain.AssetRelation;
import com.aihub.asset.domain.AssetRelationRepository;
import com.aihub.asset.domain.AssetRelationType;
import com.aihub.asset.domain.AssetRepository;
import com.aihub.audit.application.AuditEvent;
import com.aihub.audit.application.AuditService;
import com.aihub.audit.domain.AuditResult;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 资产血缘关系写服务。
 */
@Service
public class AssetRelationApplicationService {

    private static final Logger LOG = LoggerFactory.getLogger(AssetRelationApplicationService.class);

    private final AssetRepository assetRepository;
    private final AssetRelationRepository relationRepository;
    private final AssetAccessPolicy accessPolicy;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public AssetRelationApplicationService(AssetRepository assetRepository,
                                           AssetRelationRepository relationRepository,
                                           AssetAccessPolicy accessPolicy,
                                           AuthorizationService authorizationService,
                                           AuditService auditService,
                                           IdGenerator idGenerator,
                                           Clock clock) {
        this.assetRepository = assetRepository;
        this.relationRepository = relationRepository;
        this.accessPolicy = accessPolicy;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /**
     * 创建资产血缘关系边（当前资产为 parent，childAssetId 为下游）。
     */
    @Transactional
    public AssetLineageView.RelationEdge createRelation(String assetId,
                                                        String childAssetId,
                                                        AssetRelationType relationType,
                                                        String principalId) {
        authorizationService.requirePermission(Permissions.ASSET_MANAGE);
        Asset parent = loadAccessible(assetId, principalId);
        if (childAssetId == null || childAssetId.isBlank()) {
            throw new ValidationException("childAssetId is required");
        }
        if (relationType == null) {
            throw new ValidationException("relationType is required");
        }
        if (assetId.equals(childAssetId)) {
            throw new ValidationException("parent and child asset must differ");
        }
        loadAccessible(childAssetId, principalId);

        String relationId = idGenerator.generate(IdPrefix.RELATION);
        Instant now = clock.instant();
        AssetRelation relation = new AssetRelation(
                relationId, assetId, childAssetId, relationType, principalId, now);
        try {
            relationRepository.insert(relation);
        } catch (DuplicateKeyException ex) {
            throw new ConflictException(
                    ErrorCode.ASSET_ALREADY_EXISTS,
                    "asset relation already exists",
                    Map.of("parentAssetId", assetId,
                            "childAssetId", childAssetId,
                            "relationType", relationType.name()));
        }

        auditRelation("ASSET_RELATION_CREATED", principalId, parent.assetId(), Map.of(
                "relationId", relationId,
                "parentAssetId", assetId,
                "childAssetId", childAssetId,
                "relationType", relationType.name()));
        return AssetLineageView.RelationEdge.from(relation, 1);
    }

    private Asset loadAccessible(String assetId, String principalId) {
        return assetRepository.findByAssetId(assetId)
                .filter(asset -> accessPolicy.canAccess(asset, principalId))
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.ASSET_NOT_FOUND,
                        "asset not found: " + assetId,
                        Map.of()));
    }

    private void auditRelation(String eventType, String principalId, String assetId,
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
}
