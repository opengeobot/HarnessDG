/*
 * 功能: 上传物化 Job Handler，校验文件、生成 Manifest、推进版本并触发预览。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.transfer.infrastructure;

import com.aihub.asset.domain.Asset;
import com.aihub.asset.domain.AssetRepository;
import com.aihub.asset.domain.AssetType;
import com.aihub.job.domain.JobContext;
import com.aihub.job.domain.JobHandler;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.transfer.domain.UploadSession;
import com.aihub.transfer.domain.UploadSessionRepository;
import com.aihub.transfer.domain.UploadSessionStatus;
import com.aihub.version.application.PreviewApplicationService;
import com.aihub.version.domain.Artifact;
import com.aihub.version.domain.Manifest;
import com.aihub.version.domain.Version;
import com.aihub.version.domain.VersionRepository;
import com.aihub.version.domain.VersionStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 上传物化 Job Handler。
 *
 * <p>处理 {@code UPLOAD_MATERIALIZE} 类型任务：
 * <ol>
 *   <li>校验上传文件（路径安全、SHA-256 一致性）</li>
 *   <li>生成 Manifest 并计算摘要</li>
 *   <li>将工件记录写入 version_artifact 表</li>
 *   <li>将版本状态推进到 VALIDATING</li>
 *   <li>标记上传会话为 COMPLETED</li>
 *   <li>数据集资产入队 PREVIEW_GENERATE（若有样本内容）</li>
 * </ol>
 *
 * <p>幂等性保证：会话已 COMPLETED 时跳过；PROCESSING 状态正常处理。
 */
@Component
public class UploadMaterializeJobHandler implements JobHandler {

    private static final Logger LOG = LoggerFactory.getLogger(UploadMaterializeJobHandler.class);

    private final UploadSessionRepository sessionRepository;
    private final VersionRepository versionRepository;
    private final AssetRepository assetRepository;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;
    private final PreviewApplicationService previewApplicationService;

    public UploadMaterializeJobHandler(UploadSessionRepository sessionRepository,
                                       VersionRepository versionRepository,
                                       AssetRepository assetRepository,
                                       IdGenerator idGenerator,
                                       ObjectMapper objectMapper,
                                       PreviewApplicationService previewApplicationService) {
        this.sessionRepository = sessionRepository;
        this.versionRepository = versionRepository;
        this.assetRepository = assetRepository;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
        this.previewApplicationService = previewApplicationService;
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

            List<Artifact> artifacts = new ArrayList<>();
            List<Manifest.ArtifactEntry> manifestArtifacts = new ArrayList<>();
            String previewContent = null;
            String previewContentType = null;

            for (Map<String, Object> file : files) {
                String path = (String) file.get("path");
                String sha256 = (String) file.get("sha256");
                long size = ((Number) file.getOrDefault("size", 0L)).longValue();
                String mediaType = (String) file.get("mediaType");

                String artifactId = idGenerator.generate(IdPrefix.ARTIFACT);
                artifacts.add(new Artifact(artifactId, versionId, path,
                        null, null, sha256, size, mediaType));
                manifestArtifacts.add(new Manifest.ArtifactEntry(path, sha256, size, mediaType));

                if (previewContent == null && file.get("sampleContent") instanceof String sample
                        && !sample.isBlank()) {
                    previewContent = sample;
                    previewContentType = mediaType != null ? mediaType : "text/csv";
                }
            }

            Manifest manifest = Manifest.forVersion(assetId, versionId, manifestArtifacts);
            String manifestDigest = manifest.computeDigest();

            versionRepository.deleteArtifactsByVersion(versionId);
            for (Artifact artifact : artifacts) {
                versionRepository.insertArtifact(artifact);
            }

            Version version = versionRepository.findByVersionId(versionId).orElse(null);
            if (version != null && version.status() == VersionStatus.DRAFT) {
                version.bindManifestDigest(manifestDigest);
                version.transitionTo(VersionStatus.VALIDATING);
                versionRepository.update(version);
                LOG.info("version transitioned to VALIDATING versionId={} digest={}",
                        versionId, manifestDigest);
            }

            session.complete();
            sessionRepository.update(session);

            maybeEnqueuePreview(assetId, versionId, previewContent, previewContentType,
                    context.principalId());

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
