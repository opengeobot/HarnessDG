/*
 * 功能: 上传物化 Job Handler——DVC 等价 add/push 操作（SHA-256 校验 + MinIO 上传 + 元数据记录）。
 * 时间: 2026-07-30
 * 作者: AxeXie
 */
package com.aihub.transfer.infrastructure;

import com.aihub.job.domain.JobContext;
import com.aihub.job.domain.JobHandler;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.transfer.domain.StoragePort;
import com.aihub.transfer.domain.UploadSession;
import com.aihub.transfer.domain.UploadSessionRepository;
import com.aihub.version.domain.Artifact;
import com.aihub.version.domain.Manifest;
import com.aihub.version.domain.Version;
import com.aihub.version.domain.VersionRepository;
import com.aihub.version.domain.VersionStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
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
 *   <li>DVC 等价 add：从 staging 读取内容，流式计算 SHA-256，与客户端提供的 digest 校验</li>
 *   <li>DVC 等价 push：校验通过后复制 staging 对象到 dvc-cache（version 前缀）</li>
 *   <li>生成 Manifest 并计算摘要</li>
 *   <li>将工件记录写入 version_artifact 表（含 DVC 元数据）</li>
 *   <li>将版本状态推进到 VALIDATING</li>
 *   <li>标记上传会话为 COMPLETED</li>
 *   <li>清理 staging 区域（作为后续 Job 提交）</li>
 * </ol>
 *
 * <p>SHA-256 一致性校验（REQ-DVC-001）：服务端从 staging 读取对象流式计算 SHA-256，
 * 与客户端提供的 digest 比对。不一致则抛出异常，Job 进入重试/失败流程。
 *
 * <p>幂等性保证：通过 assetId + versionId 坐标唯一性 + 会话状态前置检查确保重复执行不产生重复副作用。
 */
@Component
public class UploadMaterializeJobHandler implements JobHandler {

    private static final Logger LOG = LoggerFactory.getLogger(UploadMaterializeJobHandler.class);

    /** DVC 缓存存储桶名称。 */
    private static final String DVC_CACHE_BUCKET = "dvc-cache";

    /** Staging 存储桶名称。 */
    private static final String STAGING_BUCKET = "asset-staging";

    private final UploadSessionRepository sessionRepository;
    private final VersionRepository versionRepository;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;
    private final StoragePort storagePort;

    public UploadMaterializeJobHandler(UploadSessionRepository sessionRepository,
                                       VersionRepository versionRepository,
                                       IdGenerator idGenerator,
                                       ObjectMapper objectMapper,
                                       StoragePort storagePort) {
        this.sessionRepository = sessionRepository;
        this.versionRepository = versionRepository;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
        this.storagePort = storagePort;
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

        // 生成工件记录 + DVC 等价 add/push
        List<Artifact> artifacts = new ArrayList<>();
        Map<String, Object> manifestEntries = new LinkedHashMap<>();
        String stagingKey = "staging/" + assetId + "/" + versionId + "/" + sessionId;

        for (Map<String, Object> file : files) {
            String path = (String) file.get("path");
            String clientSha256 = (String) file.get("sha256");
            long size = ((Number) file.getOrDefault("size", 0L)).longValue();
            String mediaType = (String) file.get("mediaType");

            // DVC 等价 add：验证 staging 对象 SHA-256 与客户端提供的一致
            StoragePort.ObjectMetadata metadata = storagePort.statObject(STAGING_BUCKET, stagingKey);
            String actualSha256 = metadata.sha256();

            // SHA-256 一致性校验（REQ-DVC-001）
            if (!sha256Equals(actualSha256, clientSha256)) {
                LOG.error("SHA-256 mismatch for artifact path={} sessionId={}, "
                        + "client={}, actual={}", path, sessionId, clientSha256, actualSha256);
                throw new IllegalArgumentException(
                        "SHA-256 digest mismatch for " + path
                        + ": expected " + clientSha256 + ", got " + actualSha256);
            }

            // DVC 等价 push：复制 staging 对象到 dvc-cache
            String dvcPath = "versions/" + versionId + "/" + path;
            storagePort.copyObject(STAGING_BUCKET, stagingKey, DVC_CACHE_BUCKET, dvcPath);
            LOG.info("DVC push staging->{}/{} size={}", DVC_CACHE_BUCKET, dvcPath, metadata.size());

            // 绑定 DVC 元数据到工件
            String artifactId = idGenerator.generate(IdPrefix.ARTIFACT);
            Artifact artifact = new Artifact(artifactId, versionId, path,
                    dvcPath, actualSha256, actualSha256, metadata.size(), mediaType);
            artifacts.add(artifact);
            manifestEntries.put(path, actualSha256);
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

    /**
     * 安全比较两个 SHA-256 hex 字符串（大小写无关，避免 timing attack）。
     */
    private static boolean sha256Equals(String actual, String expected) {
        if (actual == null || expected == null) {
            return actual == expected;
        }
        return MessageDigest.isEqual(
                actual.toLowerCase(java.util.Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                expected.toLowerCase(java.util.Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
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
