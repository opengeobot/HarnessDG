package com.aihub.transfer.application;

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
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.transfer.domain.StoragePort;
import com.aihub.transfer.domain.UploadSession;
import com.aihub.transfer.domain.UploadSessionRepository;
import com.aihub.transfer.domain.UploadSessionStatus;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 上传应用服务。
 *
 * <p>编排上传会话的创建、Part 签名、完成、取消，同时接入授权、审计、存储。
 */
@Service
public class UploadApplicationService {

    private static final Logger LOG = LoggerFactory.getLogger(UploadApplicationService.class);
    private static final Duration DEFAULT_SESSION_TTL = Duration.ofHours(2);
    private static final Duration PRESIGN_EXPIRY = Duration.ofMinutes(30);

    private final UploadSessionRepository sessionRepository;
    private final StoragePort storagePort;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final IdGenerator idGenerator;
    private final NotificationService notificationService;

    public UploadApplicationService(UploadSessionRepository sessionRepository,
                                    StoragePort storagePort,
                                    AuthorizationService authorizationService,
                                    AuditService auditService,
                                    IdGenerator idGenerator,
                                    NotificationService notificationService) {
        this.sessionRepository = sessionRepository;
        this.storagePort = storagePort;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.idGenerator = idGenerator;
        this.notificationService = notificationService;
    }

    /** 创建上传会话。 */
    @Transactional
    public UploadSessionView createSession(String assetId, String versionId,
                                           long totalBytes, int fileCount,
                                           String principalId) {
        authorizationService.requirePermission(Permissions.ASSET_MANAGE);
        if (totalBytes > UploadSession.MAX_SESSION_BYTES) {
            throw new ValidationException(
                    "total bytes exceeds 20GiB limit, use CLI/DVC for large uploads");
        }
        String sessionId = idGenerator.generate(IdPrefix.UPLOAD_SESSION);
        Instant expiresAt = Instant.now().plus(DEFAULT_SESSION_TTL);
        UploadSession session = UploadSession.create(
                sessionId, assetId, versionId, principalId, totalBytes, fileCount, expiresAt);

        // 在 MinIO 创建 Multipart Upload
        String objectKey = "staging/" + assetId + "/" + versionId + "/" + sessionId;
        String uploadId = storagePort.createMultipartUpload(
                "asset-staging", objectKey, "application/octet-stream");
        session.bindMinioUploadId(uploadId);

        sessionRepository.insert(session);
        auditUpload("UPLOAD_SESSION_CREATED", principalId, sessionId, assetId,
                Map.of("versionId", versionId, "totalBytes", totalBytes));
        return UploadSessionView.from(session);
    }

    /** 查询会话详情。 */
    @Transactional(readOnly = true)
    public UploadSessionView getSession(String sessionId) {
        authorizationService.requirePermission(Permissions.ASSET_READ);
        UploadSession session = loadSession(sessionId);
        return UploadSessionView.from(session);
    }

    /** 签发 Part 上传 URL。 */
    @Transactional
    public URL presignPartUpload(String sessionId, int partNumber, String principalId) {
        authorizationService.requirePermission(Permissions.ASSET_MANAGE);
        UploadSession session = loadSession(sessionId);
        if (session.status() != UploadSessionStatus.OPEN) {
            throw new ConflictException(ErrorCode.UPLOAD_SESSION_EXPIRED,
                    "session is not open: " + session.status(),
                    Map.of("sessionId", sessionId));
        }
        if (session.isExpired()) {
            session.cancel();
            sessionRepository.update(session);
            throw new ConflictException(ErrorCode.UPLOAD_SESSION_EXPIRED,
                    "session has expired", Map.of("sessionId", sessionId));
        }
        String objectKey = "staging/" + session.assetId() + "/" + session.versionId()
                + "/" + sessionId;
        return storagePort.presignPartUpload(
                "asset-staging", objectKey, session.minioUploadId(), partNumber, PRESIGN_EXPIRY);
    }

    /** 完成上传会话。 */
    @Transactional
    public UploadSessionView completeSession(String sessionId,
                                             List<StoragePort.PartInfo> parts,
                                             String principalId) {
        authorizationService.requirePermission(Permissions.ASSET_MANAGE);
        UploadSession session = loadSession(sessionId);
        try {
            session.commit();
        } catch (IllegalStateException ex) {
            throw new ConflictException(ErrorCode.UPLOAD_SESSION_EXPIRED, ex.getMessage(),
                    Map.of("sessionId", sessionId));
        }
        String objectKey = "staging/" + session.assetId() + "/" + session.versionId()
                + "/" + sessionId;
        storagePort.completeMultipartUpload(
                "asset-staging", objectKey, session.minioUploadId(), parts);
        session.complete();
        sessionRepository.update(session);
        auditUpload("UPLOAD_SESSION_COMPLETED", principalId, sessionId, session.assetId(),
                Map.of());
        publishOutbox("UPLOAD_SESSION", sessionId, "UPLOAD_COMPLETED",
                Map.of("sessionId", sessionId, "assetId", session.assetId(),
                        "versionId", session.versionId()));
        return UploadSessionView.from(session);
    }

    /** 取消上传会话。 */
    @Transactional
    public UploadSessionView cancelSession(String sessionId, String principalId) {
        authorizationService.requirePermission(Permissions.ASSET_MANAGE);
        UploadSession session = loadSession(sessionId);
        try {
            session.cancel();
        } catch (IllegalStateException ex) {
            throw new ConflictException(ErrorCode.UPLOAD_SESSION_EXPIRED, ex.getMessage(),
                    Map.of("sessionId", sessionId));
        }
        if (session.minioUploadId() != null) {
            String objectKey = "staging/" + session.assetId() + "/" + session.versionId()
                    + "/" + sessionId;
            storagePort.abortMultipartUpload("asset-staging", objectKey, session.minioUploadId());
        }
        sessionRepository.update(session);
        return UploadSessionView.from(session);
    }

    /** 列出资产下的上传会话。 */
    @Transactional(readOnly = true)
    public CursorPage<UploadSessionView> listSessions(String assetId, String cursor, int limit) {
        authorizationService.requirePermission(Permissions.ASSET_READ);
        int safeLimit = Math.max(1, Math.min(limit, 50));
        CursorPage<UploadSession> page = sessionRepository.listByAsset(assetId, cursor, safeLimit);
        return new CursorPage<>(
                page.items().stream().map(UploadSessionView::from).toList(),
                page.nextCursor(), page.hasMore());
    }

    // ---- 私有辅助 ----

    private UploadSession loadSession(String sessionId) {
        return sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.UPLOAD_SESSION_NOT_FOUND,
                        "upload session not found: " + sessionId, Map.of()));
    }

    private void auditUpload(String eventType, String principalId, String resourceId,
                             String assetId, Map<String, Object> attributes) {
        try {
            AuditEvent event = new AuditEvent(
                    eventType, eventType, principalId, null,
                    "UPLOAD_SESSION", resourceId, null, null,
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
