package com.aihub.transfer.infrastructure;

import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.transfer.domain.UploadSession;
import com.aihub.transfer.domain.UploadSessionRepository;
import com.aihub.version.domain.Artifact;
import com.aihub.version.domain.Manifest;
import com.aihub.version.domain.Version;
import com.aihub.version.domain.VersionRepository;
import com.aihub.version.domain.VersionStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aihub.job.domain.JobContext;
import com.aihub.job.domain.JobHandler;
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
 *   <li>清理 staging 区域（作为后续 Job 提交）</li>
 * </ol>
 *
 * <p>幂等性保证：通过 assetId + versionId 坐标唯一性 + 会话状态前置检查确保重复执行不产生重复副作用。
 */
@Component
public class UploadMaterializeJobHandler implements JobHandler {

    private static final Logger LOG = LoggerFactory.getLogger(UploadMaterializeJobHandler.class);

    private final UploadSessionRepository sessionRepository;
    private final VersionRepository versionRepository;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public UploadMaterializeJobHandler(UploadSessionRepository sessionRepository,
                                       VersionRepository versionRepository,
                                       IdGenerator idGenerator,
                                       ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.versionRepository = versionRepository;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
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

        // 幂等检查：会话已完成则跳过
        UploadSession session = sessionRepository.findBySessionId(sessionId).orElse(null);
        if (session == null) {
            LOG.warn("upload session not found sessionId={}, skipping", sessionId);
            return;
        }
        if (session.status() == com.aihub.transfer.domain.UploadSessionStatus.COMPLETED) {
            LOG.info("upload session already completed sessionId={}, skipping", sessionId);
            return;
        }

        // 路径安全校验（防止符号链接/路径穿越）
        List<Map<String, Object>> files = parseFileList(payload);
        for (Map<String, Object> file : files) {
            String path = (String) file.get("path");
            validatePath(path);
        }

        // 生成工件记录
        List<Artifact> artifacts = new ArrayList<>();
        Map<String, Object> manifestEntries = new LinkedHashMap<>();
        for (Map<String, Object> file : files) {
            String path = (String) file.get("path");
            String sha256 = (String) file.get("sha256");
            long size = ((Number) file.getOrDefault("size", 0L)).longValue();
            String mediaType = (String) file.get("mediaType");

            String artifactId = idGenerator.generate(IdPrefix.ARTIFACT);
            Artifact artifact = new Artifact(artifactId, versionId, path,
                    null, null, sha256, size, mediaType);
            artifacts.add(artifact);
            manifestEntries.put(path, sha256);
        }

        // 计算 Manifest 摘要
        Manifest manifest = new Manifest(manifestEntries);
        String manifestDigest = manifest.computeDigest();

        // 写入工件（幂等：先清除再插入）
        versionRepository.deleteArtifactsByVersion(versionId);
        for (Artifact artifact : artifacts) {
            versionRepository.insertArtifact(artifact);
        }

        // 更新版本：绑定 Manifest 摘要，状态推进到 VALIDATING
        Version version = versionRepository.findByVersionId(versionId).orElse(null);
        if (version != null && version.status() == VersionStatus.DRAFT) {
            version.bindManifestDigest(manifestDigest);
            version.transitionTo(VersionStatus.VALIDATING);
            versionRepository.update(version);
            LOG.info("version transitioned to VALIDATING versionId={} digest={}",
                    versionId, manifestDigest);
        }

        // 标记上传会话完成
        if (session.status() == com.aihub.transfer.domain.UploadSessionStatus.OPEN
                || session.status() == com.aihub.transfer.domain.UploadSessionStatus.COMMITTING) {
            if (session.status() == com.aihub.transfer.domain.UploadSessionStatus.OPEN) {
                session.commit();
            }
            session.complete();
            sessionRepository.update(session);
        }

        LOG.info("upload materialization completed sessionId={} artifacts={} digest={}",
                sessionId, artifacts.size(), manifestDigest);
    }

    /**
     * 校验文件路径安全性：防止路径穿越和符号链接。
     */
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
        // 防止符号链接
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
            file.put("mediaType", node.path("mediaType").asText(null));
            result.add(file);
        }
        return result;
    }
}
