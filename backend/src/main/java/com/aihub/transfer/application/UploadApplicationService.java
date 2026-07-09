package com.aihub.transfer.application;

import com.aihub.audit.application.AuditEvent;
import com.aihub.audit.application.AuditService;
import com.aihub.audit.domain.AuditResult;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.job.application.JobApplicationService;
import com.aihub.job.domain.Job;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 上传应用服务。
 *
 * <p>编排上传会话的创建、Part 签名、完成、取消，同时接入授权、审计、存储与物化 Job 入队。
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
    private final JobApplicationService jobApplicationService;
    private final ObjectMapper objectMapper;

    public UploadApplicationService(UploadSessionRepository sessionRepository,
                                    StoragePort storagePort,
                                    AuthorizationService authorizationService,
                                    AuditService auditService,
                                    IdGenerator idGenerator,
                                    NotificationService notificationService,
                                    JobApplicationService jobApplicationService,
                                    ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.storagePort = storagePort;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.idGenerator = idGenerator;
        this.notificationService = notificationService;
        this.jobApplicationService = jobApplicationService;
        this.objectMapper = objectMapper;
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

    /** 完成上传会话并入队物化 Job。 */
    @Transactional
    public UploadSessionView completeSession(String sessionId,
                                             List<StoragePort.PartInfo> parts,
                                             List<FileMetadata> files,
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
        try {
            storagePort.completeMultipartUpload(
                    "asset-staging", objectKey, session.minioUploadId(), parts);
        } catch (Exception ex) {
            publishOutbox("UPLOAD_SESSION", sessionId, "UPLOAD_FAILED",
                    Map.of("sessionId", sessionId, "assetId", session.assetId(),
                            "versionId", session.versionId(), "reason", ex.getMessage()));
            throw ex;
        }
        session.markProcessing();
        sessionRepository.update(session);

        String jobId = enqueueMaterializeJob(session, files, principalId);

        auditUpload("UPLOAD_SESSION_COMPLETED", principalId, sessionId, session.assetId(),
                Map.of("jobId", jobId));
        publishOutbox("UPLOAD_SESSION", sessionId, "UPLOAD_PROCESSING",
                Map.of("sessionId", sessionId, "assetId", session.assetId(),
                        "versionId", session.versionId(), "jobId", jobId));
        return UploadSessionView.from(session, jobId);
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

    private String enqueueMaterializeJob(UploadSession session, List<FileMetadata> files,
                                         String principalId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", session.sessionId());
        payload.put("assetId", session.assetId());
        payload.put("versionId", session.versionId());
        payload.put("files", toFilePayload(files, session.fileCount()));
        try {
            Job job = jobApplicationService.enqueue(
                    "UPLOAD_MATERIALIZE",
                    objectMapper.writeValueAsString(payload),
                    principalId,
                    null,
                    session.assetId(),
                    3);
            return job.jobId();
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize materialize payload", ex);
        }
    }

    private List<Map<String, Object>> toFilePayload(List<FileMetadata> files, int fileCount) {
        if (files == null || files.isEmpty()) {
            if (fileCount <= 0) {
                return List.of();
            }
            List<Map<String, Object>> placeholders = new ArrayList<>();
            for (int i = 0; i < fileCount; i++) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("path", "upload-" + (i + 1) + ".bin");
                entry.put("sha256", "");
                entry.put("size", 0L);
                placeholders.add(entry);
            }
            return placeholders;
        }
        return files.stream().map(f -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("path", f.path());
            entry.put("sha256", f.sha256() != null ? f.sha256() : "");
            entry.put("size", f.size());
            if (f.mediaType() != null) {
                entry.put("mediaType", f.mediaType());
            }
            if (f.sampleContent() != null && !f.sampleContent().isBlank()) {
                entry.put("sampleContent", f.sampleContent());
            }
            return entry;
        }).toList();
    }

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

    /** 上传文件元数据（完成会话时携带）。 */
    public record FileMetadata(String path, String sha256, long size,
                               String mediaType, String sampleContent) {}
}
