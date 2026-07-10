/*
 * 功能: 上传物化 Job Handler——校验、MinIO 验签、Manifest、Gitea 提交与预览入队。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.transfer.infrastructure;

import com.aihub.asset.domain.Asset;
import com.aihub.asset.domain.AssetRepository;
import com.aihub.asset.domain.AssetType;
import com.aihub.audit.application.AuditEvent;
import com.aihub.audit.application.AuditService;
import com.aihub.audit.domain.AuditResult;
import com.aihub.job.application.JobApplicationService;
import com.aihub.job.domain.JobContext;
import com.aihub.job.domain.JobHandler;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.transfer.domain.StoragePort;
import com.aihub.transfer.domain.UploadSession;
import com.aihub.transfer.domain.UploadSessionRepository;
import com.aihub.transfer.domain.UploadSessionStatus;
import com.aihub.version.application.PreviewApplicationService;
import com.aihub.version.domain.Artifact;
import com.aihub.version.domain.Manifest;
import com.aihub.version.domain.Version;
import com.aihub.version.domain.VersionMaterializationPort;
import com.aihub.version.domain.VersionMaterializationPort.DvcPointer;
import com.aihub.version.domain.VersionMaterializationPort.MaterializationRequest;
import com.aihub.version.domain.VersionMaterializationPort.MaterializationResult;
import com.aihub.version.domain.VersionRepository;
import com.aihub.version.domain.VersionStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 上传物化 Job Handler。
 */
@Component
public class UploadMaterializeJobHandler implements JobHandler {

    private static final Logger LOG = LoggerFactory.getLogger(UploadMaterializeJobHandler.class);
    private static final String STAGING_BUCKET = "asset-staging";

    private final UploadSessionRepository sessionRepository;
    private final VersionRepository versionRepository;
    private final AssetRepository assetRepository;
    private final StoragePort storagePort;
    private final VersionMaterializationPort materializationPort;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;
    private final PreviewApplicationService previewApplicationService;
    private final JobApplicationService jobApplicationService;
    private final AuditService auditService;

    public UploadMaterializeJobHandler(UploadSessionRepository sessionRepository,
                                       VersionRepository versionRepository,
                                       AssetRepository assetRepository,
                                       StoragePort storagePort,
                                       VersionMaterializationPort materializationPort,
                                       IdGenerator idGenerator,
                                       ObjectMapper objectMapper,
                                       PreviewApplicationService previewApplicationService,
                                       JobApplicationService jobApplicationService,
                                       AuditService auditService) {
        this.sessionRepository = sessionRepository;
        this.versionRepository = versionRepository;
        this.assetRepository = assetRepository;
        this.storagePort = storagePort;
        this.materializationPort = materializationPort;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
        this.previewApplicationService = previewApplicationService;
        this.jobApplicationService = jobApplicationService;
        this.auditService = auditService;
    }

    @Override
    public String type() {
        return "UPLOAD_MATERIALIZE";
    }

    @Override
    public void handle(JobContext context) throws Exception {
        JsonNode payload = objectMapper.readTree(context.payload());
        String sessionId = payload.path("sessionId").asText();
        String assetId = payload.path("assetId").asText(context.assetId());
        String versionId = payload.path("versionId").asText();

        LOG.info("materializing upload sessionId={} assetId={} versionId={}",
                sessionId, assetId, versionId);

        UploadSession session = sessionRepository.findBySessionId(sessionId).orElse(null);
        if (session == null) {
            LOG.warn("upload session not found sessionId={}, skipping", sessionId);
            return;
        }
        if (session.status() == UploadSessionStatus.COMPLETED) {
            LOG.info("upload session already completed sessionId={}, skipping", sessionId);
            return;
        }
        if (session.status() != UploadSessionStatus.PROCESSING
                && session.status() != UploadSessionStatus.COMMITTING) {
            LOG.warn("upload session not materializable sessionId={} status={}",
                    sessionId, session.status());
            return;
        }

        try {
            List<Map<String, Object>> files = parseFileList(payload);
            for (Map<String, Object> file : files) {
                validatePath((String) file.get("path"));
            }

            verifyStagingObjects(session, files);

            List<Artifact> artifacts = new ArrayList<>();
            List<Manifest.ArtifactEntry> manifestArtifacts = new ArrayList<>();
            List<DvcPointer> dvcPointers = new ArrayList<>();
            String previewContent = null;
            String previewContentType = null;

            for (Map<String, Object> file : files) {
                String path = (String) file.get("path");
                String sha256 = (String) file.get("sha256");
                long size = ((Number) file.getOrDefault("size", 0L)).longValue();
                String mediaType = (String) file.get("mediaType");

                String dvcHash = sha256ToDvcMd5(sha256);
                String dvcFile = path + ".dvc";
                String artifactId = idGenerator.generate(IdPrefix.ARTIFACT);
                Artifact artifact = new Artifact(artifactId, versionId, path,
                        dvcFile, dvcHash, sha256, size, mediaType);
                artifacts.add(artifact);
                manifestArtifacts.add(new Manifest.ArtifactEntry(path, sha256, size, mediaType));
                dvcPointers.add(new DvcPointer(dvcFile, buildDvcPointerContent(path, dvcHash, size)));

                if (previewContent == null && file.get("sampleContent") instanceof String sample
                        && !sample.isBlank()) {
                    previewContent = sample;
                    previewContentType = mediaType != null ? mediaType : "text/csv";
                }
            }

            Manifest manifest = Manifest.forVersion(assetId, versionId, manifestArtifacts);
            String manifestDigest = manifest.computeDigest();
            String manifestJson = objectMapper.writeValueAsString(manifest.entries());

            MaterializationResult gitResult = materializeToGit(assetId, versionId, manifestJson, dvcPointers);
            String sourceCommit = resolveSourceCommit(gitResult, manifestDigest);

            versionRepository.deleteArtifactsByVersion(versionId);
            for (Artifact artifact : artifacts) {
                versionRepository.insertArtifact(artifact);
            }

            Version version = versionRepository.findByVersionId(versionId).orElse(null);
            if (version != null && version.status() == VersionStatus.DRAFT) {
                version.bindManifestDigest(manifestDigest);
                if (sourceCommit != null) {
                    version.bindSourceCommit(sourceCommit);
                }
                version.transitionTo(VersionStatus.VALIDATING);
                versionRepository.update(version);
                LOG.info("version transitioned to VALIDATING versionId={} digest={} sourceCommit={}",
                        versionId, manifestDigest, sourceCommit);
            }

            session.complete();
            sessionRepository.update(session);

            enqueueStagingCleanup(session, context.principalId());
            maybeEnqueuePreview(assetId, versionId, previewContent, previewContentType,
                    context.principalId());

            auditMaterialization(context.principalId(), sessionId, assetId, versionId,
                    manifestDigest, sourceCommit, gitResult);

            LOG.info("upload materialization completed sessionId={} artifacts={} digest={}",
                    sessionId, artifacts.size(), manifestDigest);
        } catch (Exception ex) {
            if (session.status() == UploadSessionStatus.PROCESSING) {
                session.markFailed();
                sessionRepository.update(session);
            }
            throw ex;
        }
    }

    private void verifyStagingObjects(UploadSession session, List<Map<String, Object>> files) {
        String sessionKey = stagingObjectKey(session);
        if (storagePort.objectExists(STAGING_BUCKET, sessionKey)) {
            Optional<String> actual = storagePort.sha256Hex(STAGING_BUCKET, sessionKey);
            if (files.size() == 1 && actual.isPresent()) {
                String expected = (String) files.get(0).get("sha256");
                if (expected != null && !expected.isBlank() && !expected.equalsIgnoreCase(actual.get())) {
                    throw new IllegalArgumentException(
                            "sha256 mismatch for staging object: expected=" + expected + " actual=" + actual.get());
                }
            }
            return;
        }
        for (Map<String, Object> file : files) {
            String path = (String) file.get("path");
            String fileKey = stagingFileKey(session, path);
            if (!storagePort.objectExists(STAGING_BUCKET, fileKey)) {
                continue;
            }
            Optional<String> actual = storagePort.sha256Hex(STAGING_BUCKET, fileKey);
            String expected = (String) file.get("sha256");
            if (expected != null && !expected.isBlank() && actual.isPresent()
                    && !expected.equalsIgnoreCase(actual.get())) {
                throw new IllegalArgumentException(
                        "sha256 mismatch for " + path + ": expected=" + expected + " actual=" + actual.get());
            }
        }
    }

    private MaterializationResult materializeToGit(String assetId, String versionId,
                                                   String manifestJson, List<DvcPointer> dvcPointers) {
        Asset asset = assetRepository.findByAssetId(assetId).orElse(null);
        if (asset == null || asset.repository() == null) {
            return new MaterializationResult(null, false, "asset has no git repository");
        }
        Version version = versionRepository.findByVersionId(versionId).orElse(null);
        String versionLiteral = version != null ? version.version() : versionId;
        return materializationPort.materialize(new MaterializationRequest(
                asset.repository().fullName(), versionLiteral, manifestJson, dvcPointers));
    }

    private String resolveSourceCommit(MaterializationResult gitResult, String manifestDigest) {
        if (gitResult.giteaBacked() && gitResult.sourceCommit() != null) {
            return gitResult.sourceCommit();
        }
        return "local-" + manifestDigest;
    }

    private void enqueueStagingCleanup(UploadSession session, String principalId) throws Exception {
        Map<String, String> payload = Map.of(
                "sessionId", session.sessionId(),
                "assetId", session.assetId(),
                "versionId", session.versionId());
        jobApplicationService.enqueue(
                "STAGING_CLEANUP",
                objectMapper.writeValueAsString(payload),
                principalId,
                null,
                session.assetId(),
                2);
    }

    private void auditMaterialization(String principalId, String sessionId, String assetId,
                                      String versionId, String manifestDigest,
                                      String sourceCommit, MaterializationResult gitResult) {
        try {
            auditService.record(new AuditEvent(
                    "UPLOAD_MATERIALIZED",
                    "upload:materialize",
                    principalId,
                    null,
                    "UPLOAD_SESSION",
                    sessionId,
                    null,
                    null,
                    AuditResult.SUCCEEDED,
                    null,
                    Map.of(
                            "assetId", assetId,
                            "versionId", versionId,
                            "manifestDigest", manifestDigest,
                            "sourceCommit", sourceCommit,
                            "giteaBacked", gitResult.giteaBacked(),
                            "note", gitResult.note() == null ? "" : gitResult.note())));
        } catch (Exception ex) {
            LOG.warn("failed to audit materialization sessionId={}", sessionId, ex);
        }
    }

    private static String stagingObjectKey(UploadSession session) {
        return "staging/" + session.assetId() + "/" + session.versionId() + "/" + session.sessionId();
    }

    private static String stagingFileKey(UploadSession session, String path) {
        return stagingObjectKey(session) + "/files/" + path;
    }

    private static String sha256ToDvcMd5(String sha256) {
        if (sha256 == null || sha256.length() < 32) {
            return sha256;
        }
        return sha256.substring(0, 32);
    }

    private static String buildDvcPointerContent(String path, String md5, long size) {
        return "outs:\n- md5: " + md5 + "\n  size: " + size + "\n  path: " + path + "\n";
    }

    private void maybeEnqueuePreview(String assetId, String versionId, String content,
                                     String contentType, String principalId) {
        Asset asset = assetRepository.findByAssetId(assetId).orElse(null);
        if (asset == null || asset.type() != AssetType.DATASET) {
            return;
        }
        if (content == null || content.isBlank()) {
            LOG.debug("no preview sample content for dataset assetId={} versionId={}", assetId, versionId);
            return;
        }
        String jobId = previewApplicationService.enqueuePreview(
                assetId, versionId, content, contentType, principalId);
        LOG.info("enqueued PREVIEW_GENERATE jobId={} assetId={} versionId={}", jobId, assetId, versionId);
    }

    private void validatePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("file path is required");
        }
        if (path.contains("..") || path.startsWith("/") || path.startsWith("\\")) {
            throw new IllegalArgumentException("unsafe file path: " + path);
        }
        if (path.contains("\0")) {
            throw new IllegalArgumentException("null byte in file path: " + path);
        }
        if (path.startsWith("~") || path.contains("->")) {
            throw new IllegalArgumentException("suspected symlink in path: " + path);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseFileList(JsonNode payload) {
        JsonNode filesNode = payload.path("files");
        if (filesNode.isMissingNode() || !filesNode.isArray()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode node : filesNode) {
            Map<String, Object> file = new LinkedHashMap<>();
            file.put("path", node.path("path").asText(""));
            file.put("sha256", node.path("sha256").asText(""));
            file.put("size", node.path("size").asLong(0L));
            if (!node.path("mediaType").isMissingNode() && !node.path("mediaType").isNull()) {
                file.put("mediaType", node.path("mediaType").asText());
            }
            if (!node.path("sampleContent").isMissingNode() && !node.path("sampleContent").isNull()) {
                file.put("sampleContent", node.path("sampleContent").asText());
            }
            result.add(file);
        }
        return result;
    }
}
