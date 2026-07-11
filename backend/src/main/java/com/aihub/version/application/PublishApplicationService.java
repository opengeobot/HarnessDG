package com.aihub.version.application;

import com.aihub.audit.application.AuditEvent;
import com.aihub.audit.application.AuditService;
import com.aihub.audit.domain.AuditResult;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.job.application.JobApplicationService;
import com.aihub.notification.application.NotificationRecipientResolver;
import com.aihub.notification.application.NotificationService;
import com.aihub.notification.domain.NotificationSeverity;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.aihub.version.domain.Version;
import com.aihub.version.domain.VersionRepository;
import com.aihub.version.domain.VersionStatus;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 发布应用服务。
 *
 * <p>编排提交审批、审批决策与发布 Saga 触发。
 * 默认提交人不能审批自己。审批全部完成后创建唯一 Publish Job。
 */
@Service
public class PublishApplicationService {

    private static final Logger LOG = LoggerFactory.getLogger(PublishApplicationService.class);

    private final VersionRepository versionRepository;
    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final JobApplicationService jobApplicationService;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final NotificationRecipientResolver recipientResolver;

    public PublishApplicationService(VersionRepository versionRepository,
                                     JdbcTemplate jdbcTemplate,
                                     IdGenerator idGenerator,
                                     AuthorizationService authorizationService,
                                     AuditService auditService,
                                     JobApplicationService jobApplicationService,
                                     ObjectMapper objectMapper,
                                     NotificationService notificationService,
                                     NotificationRecipientResolver recipientResolver) {
        this.versionRepository = versionRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.jobApplicationService = jobApplicationService;
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.recipientResolver = recipientResolver;
    }

    /**
     * 提交发布请求。
     *
     * @param versionId 版本 ID（必须处于 PENDING_REVIEW 状态）
     * @return 请求 ID
     */
    @Transactional
    public String submitPublishRequest(String versionId) {
        authorizationService.requirePermission("asset:write");

        Version version = versionRepository.findByVersionId(versionId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.VERSION_NOT_FOUND,
                        "version not found", Map.of("versionId", versionId)));

        if (version.status() != VersionStatus.PENDING_REVIEW) {
            throw new IllegalStateException("only PENDING_REVIEW versions can be submitted for publish");
        }

        // 幂等：检查是否已存在请求（先于校验报告检查，支持重放）
        Long existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM publish_request WHERE version_id = ?",
                Long.class, versionId);
        if (existing != null && existing > 0) {
            String existingId = jdbcTemplate.queryForObject(
                    "SELECT request_id FROM publish_request WHERE version_id = ?",
                    String.class, versionId);
            LOG.info("publish request already exists for versionId={} requestId={}", versionId, existingId);
            return existingId;
        }

        String digest = version.manifestDigest();
        if (digest == null || digest.isBlank()) {
            throw new IllegalStateException("version must have a manifest digest before publishing");
        }

        // 要求最新校验报告通过
        Map<String, Object> latestReport = jdbcTemplate.queryForList("""
                SELECT status, policy_version FROM validation_report
                WHERE version_id = ? ORDER BY created_at DESC LIMIT 1
                """, versionId).stream().findFirst().orElse(null);
        if (latestReport == null || !"PASSED".equals(String.valueOf(latestReport.get("status")))) {
            throw new IllegalStateException("version must have a PASSED validation report before submit");
        }

        String sourceCommit = version.sourceCommit();
        if (sourceCommit == null || sourceCommit.isBlank()) {
            throw new IllegalStateException("version must have a source commit before publishing");
        }

        String requestId = idGenerator.generate(IdPrefix.PUBLISH_REQUEST);
        String principalId = PrincipalContextHolder.current()
                .map(c -> c.principalId())
                .orElseThrow(() -> new IllegalStateException("no authenticated principal"));

        jdbcTemplate.update("""
                INSERT INTO publish_request (request_id, version_id, frozen_digest, frozen_source_commit, policy_version, status, submitted_by)
                VALUES (?, ?, ?, ?, ?, 'SUBMITTED', ?)
                """, requestId, versionId, digest, sourceCommit,
                String.valueOf(latestReport.get("policy_version")), principalId);

        auditService.record(new AuditEvent(
                "PUBLISH_REQUEST_SUBMITTED", "publish:submit",
                principalId, null, "VERSION", versionId, null, null,
                AuditResult.SUCCEEDED, null,
                Map.of("requestId", requestId)));

        LOG.info("publish request submitted versionId={} requestId={}", versionId, requestId);
        Map<String, Object> payload = Map.of("requestId", requestId, "versionId", versionId,
                "assetId", version.assetId(), "version", version.version(), "requestedBy", principalId);
        publishOutbox("PUBLISH_REQUEST", requestId, "VERSION_REVIEW_REQUESTED", payload);
        notificationService.fanOutInAppNotifications(
                recipientResolver.resolveReviewers(version.assetId()),
                principalId,
                "VERSION_REVIEW_REQUESTED",
                "notification.version.review_requested",
                NotificationSeverity.INFO,
                payload);
        return requestId;
    }

    /**
     * 审批决策。
     *
     * @param requestId 请求 ID
     * @param decision  APPROVE / REJECT / REQUEST_CHANGES
     * @param comments  审批意见
     */
    @Transactional
    public void submitDecision(String requestId, String decision, String comments) {
        authorizationService.requirePermission(Permissions.ASSET_REVIEW);

        String reviewerId = PrincipalContextHolder.current()
                .map(c -> c.principalId())
                .orElseThrow(() -> new IllegalStateException("no authenticated principal"));

        // 查询请求
        Map<String, Object> request = jdbcTemplate.queryForMap(
                "SELECT request_id, version_id, submitted_by, status, frozen_digest, frozen_source_commit FROM publish_request WHERE request_id = ?",
                requestId);

        String submittedBy = String.valueOf(request.get("submitted_by"));
        String status = String.valueOf(request.get("status"));
        String versionId = String.valueOf(request.get("version_id"));

        // 提交人不能审批自己
        if (submittedBy.equals(reviewerId)) {
            throw new IllegalStateException("submitter cannot approve their own publish request");
        }

        if (!"SUBMITTED".equals(status)) {
            throw new IllegalStateException("only SUBMITTED requests can receive decisions, current: " + status);
        }

        // 内容漂移检测：审批前重新校验冻结 revision
        Version version = versionRepository.findByVersionId(versionId).orElse(null);
        if (version == null) {
            throw new NotFoundException(ErrorCode.VERSION_NOT_FOUND,
                    "version not found", Map.of("versionId", versionId));
        }
        String frozenDigest = String.valueOf(request.get("frozen_digest"));
        Object frozenCommitObj = request.get("frozen_source_commit");
        if (version.manifestDigest() == null || !version.manifestDigest().equals(frozenDigest)) {
            throw new IllegalStateException("manifest digest drifted since submit; re-validate required");
        }
        if (frozenCommitObj != null && version.sourceCommit() != null
                && !version.sourceCommit().equalsIgnoreCase(String.valueOf(frozenCommitObj))) {
            throw new IllegalStateException("source commit drifted since submit; re-validate required");
        }

        String reviewId = idGenerator.generate(IdPrefix.REVIEW_DECISION);
        jdbcTemplate.update("""
                INSERT INTO review_decision (review_id, request_id, reviewer_id, decision, comments)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (request_id, reviewer_id) DO UPDATE SET decision = EXCLUDED.decision, comments = EXCLUDED.comments
                """, reviewId, requestId, reviewerId, decision, comments);

        auditService.record(new AuditEvent(
                "REVIEW_DECISION_SUBMITTED", "publish:review",
                reviewerId, null, "PUBLISH_REQUEST", requestId, null, null,
                AuditResult.SUCCEEDED, null,
                Map.of("reviewId", reviewId, "decision", decision)));

        // 如果是 REJECT，直接拒绝请求
        if ("REJECT".equals(decision)) {
            jdbcTemplate.update("UPDATE publish_request SET status = 'REJECTED', decided_at = ? WHERE request_id = ?",
                    Instant.now(), requestId);

            // 版本回到 DRAFT
            String versionIdForReject = String.valueOf(request.get("version_id"));
            Version versionForReject = versionRepository.findByVersionId(versionIdForReject).orElse(null);
            if (versionForReject != null && versionForReject.status() == VersionStatus.PENDING_REVIEW) {
                versionForReject.transitionTo(VersionStatus.DRAFT);
                versionRepository.update(versionForReject);
            }
            LOG.info("publish request rejected requestId={} versionId={}", requestId, versionIdForReject);
            Map<String, Object> rejectPayload = Map.of(
                    "requestId", requestId, "versionId", versionIdForReject,
                    "assetId", versionForReject == null ? "" : versionForReject.assetId(),
                    "version", versionForReject == null ? "" : versionForReject.version(),
                    "rejectedBy", reviewerId, "reasonCode", "REJECT");
            publishOutbox("PUBLISH_REQUEST", requestId, "VERSION_REJECTED", rejectPayload);
            notifySubmitter(submittedBy, "VERSION_REJECTED", "notification.version.rejected", rejectPayload);
            return;
        }

        // REQUEST_CHANGES: 要求修改，版本回 DRAFT
        if ("REQUEST_CHANGES".equals(decision)) {
            jdbcTemplate.update("UPDATE publish_request SET status = 'CHANGES_REQUESTED', decided_at = ? WHERE request_id = ?",
                    Instant.now(), requestId);
            String versionIdForChanges = String.valueOf(request.get("version_id"));
            Version versionForChanges = versionRepository.findByVersionId(versionIdForChanges).orElse(null);
            if (versionForChanges != null && versionForChanges.status() == VersionStatus.PENDING_REVIEW) {
                versionForChanges.transitionTo(VersionStatus.DRAFT);
                versionRepository.update(versionForChanges);
            }
            LOG.info("publish request changes-requested requestId={} versionId={}", requestId, versionIdForChanges);
            Map<String, Object> changesPayload = Map.of(
                    "requestId", requestId, "versionId", versionIdForChanges,
                    "assetId", versionForChanges == null ? "" : versionForChanges.assetId(),
                    "version", versionForChanges == null ? "" : versionForChanges.version(),
                    "rejectedBy", reviewerId, "reasonCode", "REQUEST_CHANGES");
            publishOutbox("PUBLISH_REQUEST", requestId, "VERSION_REJECTED", changesPayload);
            notifySubmitter(submittedBy, "VERSION_REJECTED", "notification.version.rejected", changesPayload);
            return;
        }

        // 如果是 APPROVE，检查是否达到敏感级别要求的审批法定人数
        if ("APPROVE".equals(decision)) {
            String assetId = version.assetId();
            String sensitivityCode = resolveAssetSensitivity(assetId);
            int required = ReviewQuorumPolicy.requiredApprovals(sensitivityCode);
            Long approveCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT reviewer_id) FROM review_decision "
                            + "WHERE request_id = ? AND decision = 'APPROVE'",
                    Long.class, requestId);
            long approvals = approveCount == null ? 0L : approveCount;

            if (approvals < required) {
                LOG.info("publish request awaiting quorum requestId={} approvals={}/{} sensitivity={}",
                        requestId, approvals, required, sensitivityCode);
                return;
            }

            jdbcTemplate.update("UPDATE publish_request SET status = 'APPROVED', decided_at = ? WHERE request_id = ?",
                    Instant.now(), requestId);

            // 创建发布 Job
            String versionIdForPublish = String.valueOf(request.get("version_id"));
            try {
                String payload = objectMapper.writeValueAsString(
                        Map.of("requestId", requestId, "versionId", versionIdForPublish));
                jobApplicationService.enqueue("VERSION_PUBLISH", payload, reviewerId, null, versionIdForPublish, 3);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new IllegalStateException("failed to serialize publish payload", e);
            }

            LOG.info("publish request approved, publish job submitted requestId={} versionId={} quorum={}/{}",
                    requestId, versionIdForPublish, approvals, required);
            Map<String, Object> approvedPayload = Map.of(
                    "requestId", requestId, "versionId", versionIdForPublish,
                    "assetId", version.assetId(), "version", version.version(),
                    "approvedBy", reviewerId);
            publishOutbox("PUBLISH_REQUEST", requestId, "VERSION_APPROVED", approvedPayload);
            notifySubmitter(submittedBy, "VERSION_APPROVED", "notification.version.approved", approvedPayload);
        }
    }

    private String resolveAssetSensitivity(String assetId) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT COALESCE(am.sensitivity_code, ad.sensitivity_code)
                    FROM asset a
                    LEFT JOIN asset_model am ON am.asset_id = a.asset_id
                    LEFT JOIN asset_dataset ad ON ad.asset_id = a.asset_id
                    WHERE a.asset_id = ?
                    """, String.class, assetId);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            return null;
        }
    }

    /**
     * 版本弃用。
     */
    @Transactional
    public void deprecateVersion(String versionId) {
        authorizationService.requirePermission(Permissions.ASSET_DEPRECATE);
        Version version = versionRepository.findByVersionId(versionId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.VERSION_NOT_FOUND,
                        "version not found", Map.of("versionId", versionId)));
        version.transitionTo(VersionStatus.DEPRECATED);
        versionRepository.update(version);
        auditService.record(new AuditEvent(
                "VERSION_DEPRECATED", "version:deprecate",
                null, null, "VERSION", versionId, null, null,
                AuditResult.SUCCEEDED, null, Map.of()));
        Map<String, Object> payload = Map.of("versionId", versionId, "assetId", version.assetId(),
                "version", version.version());
        publishOutbox("VERSION", versionId, "VERSION_DEPRECATED", payload);
        notificationService.fanOutInAppNotifications(
                recipientResolver.resolveAssetOwners(version.assetId()),
                null,
                "VERSION_DEPRECATED",
                "notification.version.deprecated",
                NotificationSeverity.WARN,
                payload);
        LOG.info("version deprecated versionId={}", versionId);
    }

    /**
     * 版本归档。
     */
    @Transactional
    public void archiveVersion(String versionId) {
        authorizationService.requirePermission(Permissions.ASSET_DEPRECATE);
        Version version = versionRepository.findByVersionId(versionId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.VERSION_NOT_FOUND,
                        "version not found", Map.of("versionId", versionId)));
        version.transitionTo(VersionStatus.ARCHIVED);
        versionRepository.update(version);
        auditService.record(new AuditEvent(
                "VERSION_ARCHIVED", "version:archive",
                null, null, "VERSION", versionId, null, null,
                AuditResult.SUCCEEDED, null, Map.of()));
        LOG.info("version archived versionId={}", versionId);
    }

    /** 查询发布请求列表（按 assetId 下的版本关联）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPublishRequests(String assetId) {
        authorizationService.requirePermission(Permissions.ASSET_READ);
        return jdbcTemplate.queryForList("""
                SELECT pr.request_id, pr.version_id, pr.frozen_digest, pr.frozen_source_commit,
                       pr.policy_version, pr.status, pr.submitted_by, pr.decided_at, pr.created_at
                FROM publish_request pr
                JOIN asset_version av ON pr.version_id = av.version_id
                WHERE av.asset_id = ?
                ORDER BY pr.created_at DESC
                """, assetId);
    }

    /** 查询审批决策历史。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listDecisions(String requestId) {
        authorizationService.requirePermission(Permissions.ASSET_READ);
        return jdbcTemplate.queryForList("""
                SELECT review_id, reviewer_id, decision, comments, created_at
                FROM review_decision WHERE request_id = ? ORDER BY created_at DESC
                """, requestId);
    }

    private void notifySubmitter(String submitterId, String eventType, String i18nKey,
                                 Map<String, Object> payload) {
        if (submitterId == null || submitterId.isBlank()) {
            return;
        }
        try {
            notificationService.sendInAppNotification(submitterId, eventType, i18nKey,
                    NotificationSeverity.INFO, payload);
        } catch (Exception ex) {
            LOG.warn("failed to notify submitter={} eventType={}", submitterId, eventType, ex);
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
