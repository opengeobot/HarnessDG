package com.aihub.version.application;

import com.aihub.audit.application.AuditEvent;
import com.aihub.audit.application.AuditService;
import com.aihub.audit.domain.AuditResult;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.notification.application.NotificationService;
import com.aihub.shared.api.CursorPage;
import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.version.domain.Artifact;
import com.aihub.version.domain.Version;
import com.aihub.version.domain.VersionRepository;
import com.aihub.version.domain.VersionStatus;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 版本应用服务。
 *
 * <p>编排版本的创建、查询、状态转换、发布，同时接入授权、审计。
 */
@Service
public class VersionApplicationService {

    private static final Logger LOG = LoggerFactory.getLogger(VersionApplicationService.class);

    private final VersionRepository versionRepository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final IdGenerator idGenerator;
    private final JdbcTemplate jdbcTemplate;
    private final NotificationService notificationService;

    public VersionApplicationService(VersionRepository versionRepository,
                                     AuthorizationService authorizationService,
                                     AuditService auditService,
                                     IdGenerator idGenerator,
                                     JdbcTemplate jdbcTemplate,
                                     NotificationService notificationService) {
        this.versionRepository = versionRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.idGenerator = idGenerator;
        this.jdbcTemplate = jdbcTemplate;
        this.notificationService = notificationService;
    }

    /** 创建草稿版本。 */
    @Transactional
    public VersionView createDraftVersion(String assetId, String version, String principalId) {
        authorizationService.requirePermission(Permissions.ASSET_MANAGE);
        // 坐标唯一性校验
        versionRepository.findByCoordinate(assetId, version).ifPresent(existing -> {
            throw new ConflictException(ErrorCode.ASSET_VERSION_CONFLICT,
                    "version already exists: " + version,
                    Map.of("assetId", assetId, "version", version));
        });
        String versionId = idGenerator.generate(IdPrefix.VERSION);
        Version v = Version.createDraft(versionId, assetId, version, principalId);
        versionRepository.insert(v);
        auditVersion("VERSION_CREATED", principalId, versionId, assetId,
                Map.of("version", version));
        return VersionView.from(v);
    }

    /** 查询版本详情。 */
    @Transactional(readOnly = true)
    public VersionView getVersion(String versionId) {
        authorizationService.requirePermission(Permissions.ASSET_READ);
        Version v = loadVersion(versionId);
        return VersionView.from(v);
    }

    /** 列出资产下的版本列表（游标分页）。 */
    @Transactional(readOnly = true)
    public CursorPage<VersionView> listVersions(String assetId, String cursor, int limit) {
        authorizationService.requirePermission(Permissions.ASSET_READ);
        int safeLimit = Math.max(1, Math.min(limit, 50));
        CursorPage<Version> page = versionRepository.listByAsset(assetId, cursor, safeLimit);
        return new CursorPage<>(
                page.items().stream().map(VersionView::from).toList(),
                page.nextCursor(), page.hasMore());
    }

    /** 版本状态转换。 */
    @Transactional
    public VersionView transitionVersion(String versionId, VersionStatus target, String principalId) {
        authorizationService.requirePermission(Permissions.ASSET_MANAGE);
        Version v = loadVersion(versionId);
        try {
            v.transitionTo(target);
        } catch (IllegalStateException ex) {
            throw new ConflictException(ErrorCode.VERSION_STATE_NOT_ALLOWED, ex.getMessage(),
                    Map.of("versionId", versionId, "currentStatus", v.status().name(),
                            "targetStatus", target.name()));
        }
        versionRepository.update(v);
        auditVersion("VERSION_STATUS_CHANGED", principalId, versionId, v.assetId(),
                Map.of("from", v.status().name(), "to", target.name()));
        if (target == VersionStatus.DEPRECATED) {
            publishOutbox("ASSET_VERSION", versionId, "VERSION_DEPRECATED",
                    Map.of("versionId", versionId, "assetId", v.assetId(),
                            "version", v.version()));
        }
        return VersionView.from(v);
    }

    /** 发布版本（需已处于 PENDING_REVIEW）。 */
    @Transactional
    public VersionView publishVersion(String versionId, String manifestDigest,
                                      String gitTag, String principalId) {
        authorizationService.requirePermission(Permissions.ASSET_MANAGE);
        Version v = loadVersion(versionId);
        try {
            v.publish(manifestDigest, gitTag, principalId);
        } catch (IllegalStateException ex) {
            throw new ConflictException(ErrorCode.VERSION_STATE_NOT_ALLOWED, ex.getMessage(),
                    Map.of("versionId", versionId, "currentStatus", v.status().name()));
        }
        versionRepository.update(v);
        auditVersion("VERSION_PUBLISHED", principalId, versionId, v.assetId(),
                Map.of("version", v.version(), "gitTag", gitTag));
        publishOutbox("ASSET_VERSION", versionId, "VERSION_PUBLISHED",
                Map.of("versionId", versionId, "assetId", v.assetId(),
                        "version", v.version(), "gitTag", gitTag));
        return VersionView.from(v);
    }

    /** 列出版本下的工件。 */
    @Transactional(readOnly = true)
    public List<ArtifactView> listArtifacts(String versionId) {
        authorizationService.requirePermission(Permissions.ASSET_READ);
        loadVersion(versionId); // 验证版本存在
        return versionRepository.listArtifactsByVersion(versionId)
                .stream().map(ArtifactView::from).toList();
    }

    // ---- 私有辅助 ----

    /** 查询校验报告（最新版本）。 */
    @Transactional(readOnly = true)
    public Map<String, Object> getValidationReport(String versionId) {
        authorizationService.requirePermission(Permissions.ASSET_READ);
        loadVersion(versionId); // 验证版本存在
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT report_id, policy_version, status, findings, created_at " +
                        "FROM validation_report WHERE version_id = ? ORDER BY created_at DESC LIMIT 1",
                versionId);
        if (rows.isEmpty()) {
            return Map.of("status", "NOT_FOUND");
        }
        return rows.get(0);
    }

    private Version loadVersion(String versionId) {
        return versionRepository.findByVersionId(versionId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.VERSION_NOT_FOUND,
                        "version not found: " + versionId, Map.of()));
    }

    private void auditVersion(String eventType, String principalId, String resourceId,
                              String assetId, Map<String, Object> attributes) {
        try {
            AuditEvent event = new AuditEvent(
                    eventType, eventType, principalId, null,
                    "ASSET_VERSION", resourceId, null, null,
                    AuditResult.SUCCEEDED, null, attributes);
            auditService.record(event);
        } catch (Exception ex) {
            LOG.error("failed to record audit event eventType={} resourceId={}",
                    eventType, resourceId, ex);
        }
    }

    private void publishOutbox(String aggregateType, String aggregateId, String eventType,
                               Map<String, Object> payload) {
        try {
            notificationService.publishOutboxEvent(aggregateType, aggregateId, eventType,
                    payload, Map.of());
        } catch (Exception ex) {
            LOG.warn("failed to publish outbox event eventType={} aggregateId={}",
                    eventType, aggregateId, ex);
        }
    }
}
