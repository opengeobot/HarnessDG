/*
 * 功能: 预览生成应用服务，编排 PREVIEW_GENERATE 任务入队。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.version.application;

import com.aihub.authorization.application.AuthorizationService;
import com.aihub.job.application.JobApplicationService;
import com.aihub.job.domain.Job;
import com.aihub.platform.observability.application.PlatformMetrics;
import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.error.ValidationException;
import com.aihub.transfer.domain.StoragePort;
import com.aihub.version.domain.Artifact;
import com.aihub.version.domain.PreviewQueryPort;
import com.aihub.version.domain.Version;
import com.aihub.version.domain.VersionRepository;
import com.aihub.version.domain.VersionStatus;
import com.aihub.version.infrastructure.PreviewJobHandler;
import com.aihub.version.infrastructure.PreviewProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 预览生成应用服务。
 *
 * <p>供 REST 适配器与上传物化 Job 复用，统一 PREVIEW_GENERATE 入队逻辑。
 * 图片工件渲染（TASK-P0BR-068）以服务端代理方式从 dvc-cache 流式取字节，
 * 预签名查询串与内部 Endpoint 不进入页面数据（REQ-DST-DETAIL-001）。
 */
@Service
public class PreviewApplicationService {

    private static final Logger LOG = LoggerFactory.getLogger(PreviewApplicationService.class);
    private static final String DVC_CACHE_BUCKET = "dvc-cache";

    /** 渲染白名单：服务端常量（TASK-P0BR-068）；SVG 因内嵌脚本风险排除，新增类型须经规格变更。 */
    private static final Set<String> RENDERABLE_MEDIA_TYPES =
            Set.of("image/png", "image/jpeg", "image/webp", "image/gif");

    /** 白名单媒体类型对应的路径扩展名回退推导（仅覆盖白名单四类，不与 Worker 通用推断混用）。 */
    private static final Map<String, String> RENDERABLE_PATH_SUFFIXES = Map.of(
            ".png", "image/png",
            ".jpg", "image/jpeg",
            ".jpeg", "image/jpeg",
            ".webp", "image/webp",
            ".gif", "image/gif");

    private final JobApplicationService jobApplicationService;
    private final ObjectMapper objectMapper;
    private final PreviewQueryPort previewQueryPort;
    private final AuthorizationService authorizationService;
    private final VersionRepository versionRepository;
    private final StoragePort storagePort;
    private final PreviewProperties previewProperties;
    private final PlatformMetrics platformMetrics;

    public PreviewApplicationService(JobApplicationService jobApplicationService,
                                     ObjectMapper objectMapper,
                                     PreviewQueryPort previewQueryPort,
                                     AuthorizationService authorizationService,
                                     VersionRepository versionRepository,
                                     StoragePort storagePort,
                                     PreviewProperties previewProperties,
                                     ObjectProvider<PlatformMetrics> platformMetricsProvider) {
        this.jobApplicationService = jobApplicationService;
        this.objectMapper = objectMapper;
        this.previewQueryPort = previewQueryPort;
        this.authorizationService = authorizationService;
        this.versionRepository = versionRepository;
        this.storagePort = storagePort;
        this.previewProperties = previewProperties;
        this.platformMetrics = platformMetricsProvider.getIfAvailable();
    }

    /** 预览视图。 */
    public record PreviewView(String previewId, String contentType,
                              String content, String generatedAt) {}

    /** 图片预览渲染结果：字节 + 白名单内 mediaType（TASK-P0BR-068）。 */
    public record PreviewRenderView(byte[] content, String mediaType) {}

    /**
     * 获取资产最新预览。
     *
     * @param assetId   资产 ID
     * @param versionId 版本 ID（可空）
     * @return 预览视图
     */
    @Transactional(readOnly = true)
    public PreviewView getPreview(String assetId, String versionId) {
        authorizationService.requirePermission("asset:read");
        var record = previewQueryPort.findLatestPreview(assetId, versionId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ASSET_NOT_FOUND,
                        "preview not found for asset: " + assetId, Map.of("assetId", assetId)));
        return new PreviewView(
                record.previewId(),
                record.contentType(),
                record.content(),
                record.generatedAt());
    }

    /**
     * 入队预览生成任务。
     *
     * @param assetId     资产 ID
     * @param versionId   版本 ID
     * @param content     预览源内容（可空；为空时 Worker 按 versionId 从对象存储自动取数）
     * @param contentType 内容类型
     * @param principalId 发起主体
     * @return 任务 ID
     */
    public String enqueuePreview(String assetId, String versionId, String content,
                                 String contentType, String principalId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assetId", assetId);
        payload.put("versionId", versionId);
        payload.put("contentType", contentType != null ? contentType : "text/csv");
        if (content != null && !content.isBlank()) {
            payload.put("content", content);
        }
        try {
            Job job = jobApplicationService.enqueue(
                    "PREVIEW_GENERATE",
                    objectMapper.writeValueAsString(payload),
                    principalId,
                    null,
                    assetId,
                    3);
            return job.jobId();
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize preview payload", ex);
        }
    }

    /**
     * 服务端代理渲染图片预览工件（TASK-P0BR-068）。
     *
     * <p>校验链全部先于任何存储访问：权限 → 版本归属 → PUBLISHED 状态 → artifact 归属 →
     * mediaType 白名单 → 长度预检 → 对象存在性 → 流式读取上限（双保险）。
     * 未知/跨绑定/不可见的 assetId、versionId、artifactId 一律同一 404 语义（防枚举）；
     * 非 PUBLISHED 返回 409；白名单外 415；超限 413；对象缺失 404。
     * 对象键仅由服务端 artifact 记录的 DVC hash 推导，用户输入不参与（防路径穿越/SSRF）。
     */
    @Transactional(readOnly = true)
    public PreviewRenderView renderPreviewArtifact(String assetId, String versionId, String artifactId) {
        authorizationService.requirePermission("asset:read");

        Version version = versionRepository.findByVersionId(versionId)
                .filter(v -> v.assetId().equals(assetId))
                .orElseThrow(this::previewNotFound);
        if (version.status() != VersionStatus.PUBLISHED) {
            throw new ConflictException(ErrorCode.VERSION_STATE_NOT_ALLOWED,
                    "preview rendering requires a PUBLISHED version, current: " + version.status());
        }

        Artifact artifact = versionRepository.listArtifactsByVersion(versionId).stream()
                .filter(a -> a.artifactId().equals(artifactId))
                .findFirst()
                .orElseThrow(this::previewNotFound);

        String mediaType = resolveRenderableMediaType(artifact);

        long maxRenderBytes = previewProperties.maxRenderBytes();
        if (artifact.size() > maxRenderBytes) {
            recordPreviewFailure("limit_exceeded");
            throw new ValidationException(ErrorCode.PREVIEW_TOO_LARGE,
                    "preview artifact exceeds maxRenderBytes " + maxRenderBytes);
        }

        String objectKey = PreviewJobHandler.dvcObjectKey(artifact.dvcHash(), artifact.path());
        if (!storagePort.objectExists(DVC_CACHE_BUCKET, objectKey)) {
            LOG.warn("preview source unavailable versionId={} artifactId={}", versionId, artifactId);
            recordPreviewFailure("source_unavailable");
            throw new NotFoundException(ErrorCode.PREVIEW_SOURCE_UNAVAILABLE,
                    "preview artifact object is not available", Map.of("artifactId", artifactId));
        }

        try (InputStream in = storagePort.readObject(DVC_CACHE_BUCKET, objectKey)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(
                    (int) Math.min(artifact.size() > 0 ? artifact.size() : 8192, maxRenderBytes));
            byte[] buffer = new byte[8192];
            int total = 0;
            int n;
            while ((n = in.read(buffer)) != -1) {
                total += n;
                if (total > maxRenderBytes) {
                    // 双保险：元数据 size 不可信（历史数据/并发覆盖）时仍以流式上限兜底
                    recordPreviewFailure("limit_exceeded");
                    throw new ValidationException(ErrorCode.PREVIEW_TOO_LARGE,
                            "preview artifact exceeds maxRenderBytes " + maxRenderBytes);
                }
                out.write(buffer, 0, n);
            }
            return new PreviewRenderView(out.toByteArray(), mediaType);
        } catch (IOException ex) {
            recordPreviewFailure("source_read_failed");
            throw new NotFoundException(ErrorCode.PREVIEW_SOURCE_UNAVAILABLE,
                    "preview artifact object could not be read", Map.of("artifactId", artifactId));
        }
    }

    private NotFoundException previewNotFound() {
        return new NotFoundException(ErrorCode.PREVIEW_NOT_FOUND,
                "preview artifact not found", Map.of());
    }

    /**
     * 解析 artifact 的可渲染 mediaType：优先记录值，缺失时按路径扩展名回退；
     * 白名单外（含 SVG——内嵌脚本风险）一律 415。
     */
    private String resolveRenderableMediaType(Artifact artifact) {
        String mediaType = artifact.mediaType();
        if ((mediaType == null || mediaType.isBlank()) && artifact.path() != null) {
            String lower = artifact.path().toLowerCase();
            for (Map.Entry<String, String> e : RENDERABLE_PATH_SUFFIXES.entrySet()) {
                if (lower.endsWith(e.getKey())) {
                    mediaType = e.getValue();
                    break;
                }
            }
        }
        if (mediaType == null || !RENDERABLE_MEDIA_TYPES.contains(mediaType)) {
            recordPreviewFailure("unsupported_format");
            throw new ValidationException(ErrorCode.PREVIEW_UNSUPPORTED_FORMAT,
                    "media type is not renderable for preview: " + mediaType);
        }
        return mediaType;
    }

    private void recordPreviewFailure(String reason) {
        if (platformMetrics != null) {
            platformMetrics.recordPreviewFailure(reason);
        }
    }
}
