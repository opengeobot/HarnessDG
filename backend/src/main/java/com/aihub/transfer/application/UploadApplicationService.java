/*
 * 功能: 上传应用服务——编排会话、Part 签名、完成与 upload_file/part 持久化。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
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
import com.aihub.transfer.domain.UploadFile;
import com.aihub.transfer.domain.UploadFile.UploadFileStatus;
import com.aihub.transfer.domain.UploadFileRepository;
import com.aihub.transfer.domain.UploadPart;
import com.aihub.transfer.domain.UploadPartRepository;
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
 */
@Service
public class UploadApplicationService {

    private static final Logger LOG = LoggerFactory.getLogger(UploadApplicationService.class);
    private static final Duration DEFAULT_SESSION_TTL = Duration.ofHours(2);
    private static final Duration PRESIGN_EXPIRY = Duration.ofMinutes(30);
    private static final String STAGING_BUCKET = "asset-staging";
    private static final String SESSION_BUNDLE_PATH = "_session_bundle";

    private final UploadSessionRepository sessionRepository;
    private final UploadFileRepository uploadFileRepository;
    private final UploadPartRepository uploadPartRepository;
    private final StoragePort storagePort;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final IdGenerator idGenerator;
    private final NotificationService notificationService;
    private final JobApplicationService jobApplicationService;
    private final ObjectMapper objectMapper;

    public UploadApplicationService(UploadSessionRepository sessionRepository,
                                    UploadFileRepository uploadFileRepository,
                                    UploadPartRepository uploadPartRepository,
                                    StoragePort storagePort,
                                    AuthorizationService authorizationService,
                                    AuditService auditService,
                                    IdGenerator idGenerator,
                                    NotificationService notificationService,
                                    JobApplicationService jobApplicationService,
                                    ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.uploadFileRepository = uploadFileRepository;
        this.uploadPartRepository = uploadPartRepository;
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

        String objectKey = stagingObjectKey(assetId, versionId, sessionId);
        String uploadId = storagePort.createMultipartUpload(STAGING_BUCKET, objectKey, "application/octet-stream");
        session.bindMinioUploadId(uploadId);

        sessionRepository.insert(session);

        String bundleFileId = idGenerator.generate(IdPrefix.UPLOAD_FILE);
        uploadFileRepository.insert(new UploadFile(
                bundleFileId, sessionId, SESSION_BUNDLE_PATH, totalBytes,
                null, "application/octet-stream", Math.max(1, fileCount),
                UploadFileStatus.UPLOADING));

        auditUpload("UPLOAD_SESSION_CREATED", principalId, sessionId, assetId,
                Map.of("versionId", versionId, "totalBytes", totalBytes, "bundleFileId", bundleFileId));
        return UploadSessionView.from(session);
    }

    /** 查询会话详情（含已上传分片状态）。 */
    @Transactional(readOnly = true)
    public UploadSessionView getSession(String sessionId) {
        authorizationService.requirePermission(Permissions.ASSET_READ);
        UploadSession session = loadSession(sessionId);
        return toViewWithParts(session);
    }

    /** 按资产查询会话详情（含已上传分片状态）。 */
    @Transactional(readOnly = true)
    public UploadSessionView getSessionForAsset(String assetId, String sessionId) {
        authorizationService.requirePermission(Permissions.ASSET_READ);
        UploadSession session = loadSession(sessionId);
        if (!session.assetId().equals(assetId)) {
            throw new NotFoundException(ErrorCode.UPLOAD_SESSION_NOT_FOUND,
                    "upload session not found for asset", Map.of("assetId", assetId, "sessionId", sessionId));
        }
        return toViewWithParts(session);
    }

    private UploadSessionView toViewWithParts(UploadSession session) {
        List<UploadPartView> parts = uploadFileRepository.findSessionBundleFile(session.sessionId())
                .map(bundle -> uploadPartRepository.listByFileId(bundle.fileId()).stream()
                        .map(UploadPartView::from)
                        .toList())
                .orElse(List.of());
        return UploadSessionView.from(session, null, parts);
    }

    /** 签发 Part 上传 URL。 */
    @Transactional
    public URL presignPartUpload(String sessionId, int partNumber, String principalId) {
        authorizationService.requirePermission(Permissions.ASSET_MANAGE);
        UploadSession session = loadSession(sessionId);
        ensureSessionOpen(session, sessionId);

        UploadFile bundleFile = uploadFileRepository.findSessionBundleFile(sessionId)
                .orElseThrow(() -> new IllegalStateException("session bundle file missing: " + sessionId));

        String objectKey = stagingObjectKey(session.assetId(), session.versionId(), sessionId);
        URL url = storagePort.presignPartUpload(
                STAGING_BUCKET, objectKey, session.minioUploadId(), partNumber, PRESIGN_EXPIRY);

        uploadPartRepository.upsert(new UploadPart(
                bundleFile.fileId(), partNumber, 0L, null, url.toString(), null));

        return url;
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

        UploadFile bundleFile = uploadFileRepository.findSessionBundleFile(sessionId).orElse(null);
        String objectKey = stagingObjectKey(session.assetId(), session.versionId(), sessionId);
        try {
            storagePort.completeMultipartUpload(
                    STAGING_BUCKET, objectKey, session.minioUploadId(), parts);
        } catch (Exception ex) {
            publishOutbox("UPLOAD_SESSION", sessionId, "UPLOAD_FAILED",
                    Map.of("sessionId", sessionId, "assetId", session.assetId(),
                            "versionId", session.versionId(), "reason", ex.getMessage()));
            throw ex;
        }

        if (bundleFile != null) {
            for (StoragePort.PartInfo part : parts) {
                uploadPartRepository.markUploaded(bundleFile.fileId(), part.partNumber(), part.etag(), 0L);
            }
            uploadFileRepository.update(new UploadFile(
                    bundleFile.fileId(), bundleFile.sessionId(), bundleFile.path(),
                    bundleFile.size(), bundleFile.sha256(), bundleFile.mediaType(),
                    parts.size(), UploadFileStatus.COMPLETED));
        }

        persistCompletedFiles(sessionId, files, session.fileCount());

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
            String objectKey = stagingObjectKey(session.assetId(), session.versionId(), sessionId);
            storagePort.abortMultipartUpload(STAGING_BUCKET, objectKey, session.minioUploadId());
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

    private void persistCompletedFiles(String sessionId, List<FileMetadata> files, int fileCount) {
        List<FileMetadata> effective = files == null || files.isEmpty()
                ? placeholderFiles(fileCount) : files;
        for (FileMetadata file : effective) {
            String fileId = idGenerator.generate(IdPrefix.UPLOAD_FILE);
            uploadFileRepository.insert(new UploadFile(
                    fileId, sessionId, file.path(),
                    file.size(), file.sha256(), file.mediaType(),
                    1, UploadFileStatus.COMPLETED));
        }
    }

    private List<FileMetadata> placeholderFiles(int fileCount) {
        if (fileCount <= 0) {
            return List.of();
        }
        List<FileMetadata> placeholders = new ArrayList<>();
        for (int i = 0; i < fileCount; i++) {
            placeholders.add(new FileMetadata("upload-" + (i + 1) + ".bin", "", 0L, null, null));
        }
        return placeholders;
    }

    private void ensureSessionOpen(UploadSession session, String sessionId) {
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
    }

    private static String stagingObjectKey(String assetId, String versionId, String sessionId) {
        return "staging/" + assetId + "/" + versionId + "/" + sessionId;
    }

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
