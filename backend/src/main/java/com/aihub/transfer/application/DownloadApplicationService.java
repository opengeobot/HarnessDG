package com.aihub.transfer.application;

import com.aihub.asset.domain.Asset;
import com.aihub.asset.domain.AssetRepository;
import com.aihub.audit.application.AuditEvent;
import com.aihub.audit.application.AuditService;
import com.aihub.audit.domain.AuditResult;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.aihub.transfer.domain.StoragePort;
import com.aihub.version.domain.Artifact;
import com.aihub.version.domain.Version;
import com.aihub.version.domain.VersionRepository;
import com.aihub.version.domain.VersionStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 下载票据签发应用服务。
 *
 * <p>按权限/状态/敏感度签发 PRESIGNED_URL 或 GIT_DVC 方法。
 * 15 分钟 TTL，最小对象权限，100% 审计。
 */
@Service
public class DownloadApplicationService {

    private static final Logger LOG = LoggerFactory.getLogger(DownloadApplicationService.class);

    /** 预签名 URL 有效期：15 分钟。 */
    private static final Duration PRESIGN_TTL = Duration.ofMinutes(15);

    private final VersionRepository versionRepository;
    private final AssetRepository assetRepository;
    private final StoragePort storagePort;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public DownloadApplicationService(VersionRepository versionRepository,
                                      AssetRepository assetRepository,
                                      StoragePort storagePort,
                                      AuthorizationService authorizationService,
                                      AuditService auditService) {
        this.versionRepository = versionRepository;
        this.assetRepository = assetRepository;
        this.storagePort = storagePort;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    /**
     * 签发版本工件的下载票据。
     *
     * @param versionId  版本 ID
     * @param artifactId 工件 ID（可空，空时签发整个版本 Manifest）
     * @return 下载票据视图
     */
    public DownloadTicket issueTicket(String versionId, String artifactId) {
        // 查询版本
        Version version = versionRepository.findByVersionId(versionId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.VERSION_NOT_FOUND,
                        "version not found", Map.of("versionId", versionId)));

        // 检查版本状态：仅 PUBLISHED 版本可下载
        if (version.status() != VersionStatus.PUBLISHED) {
            throw new IllegalStateException("only PUBLISHED versions can be downloaded, current: " + version.status());
        }

        // 授权检查
        authorizationService.requirePermission("asset:read");

        // 查询资产以获取存储桶信息
        Asset asset = assetRepository.findByAssetId(version.assetId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.ASSET_NOT_FOUND,
                        "asset not found", Map.of("assetId", version.assetId())));

        String bucket = resolveBucket(asset);

        if (artifactId != null) {
            // 签发单工件下载票据
            Artifact artifact = versionRepository.listArtifactsByVersion(versionId).stream()
                    .filter(a -> a.artifactId().equals(artifactId))
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException(ErrorCode.VERSION_NOT_FOUND,
                            "artifact not found", Map.of("artifactId", artifactId)));

            String objectKey = resolveObjectKey(version, artifact);
            var url = storagePort.presignDownload(bucket, objectKey, PRESIGN_TTL);

            auditService.record(new AuditEvent(
                    "DOWNLOAD_TICKET_ISSUED",
                    "download:presign",
                    PrincipalContextHolder.current().map(c -> c.principalId()).orElse(null),
                    PrincipalContextHolder.current().map(c -> c.principalType() == null ? null : c.principalType().name()).orElse(null),
                    "VERSION",
                    versionId,
                    null,
                    null,
                    AuditResult.SUCCEEDED,
                    null,
                    Map.of("artifactId", artifactId, "method", "PRESIGNED_URL")));

            LOG.info("issued PRESIGNED_URL download ticket versionId={} artifactId={}", versionId, artifactId);
            return new DownloadTicket("PRESIGNED_URL", url.toString(), Instant.now().plus(PRESIGN_TTL),
                    artifact.path(), artifact.size());
        } else {
            // 签发 GIT_DVC 方法（客户端通过 DVC 拉取）
            auditService.record(new AuditEvent(
                    "DOWNLOAD_TICKET_ISSUED",
                    "download:dvc",
                    PrincipalContextHolder.current().map(c -> c.principalId()).orElse(null),
                    PrincipalContextHolder.current().map(c -> c.principalType() == null ? null : c.principalType().name()).orElse(null),
                    "VERSION",
                    versionId,
                    null,
                    null,
                    AuditResult.SUCCEEDED,
                    null,
                    Map.of("method", "GIT_DVC")));

            LOG.info("issued GIT_DVC download ticket versionId={}", versionId);
            return new DownloadTicket("GIT_DVC", null, null, null, null);
        }
    }

    private String resolveBucket(Asset asset) {
        // P2 简化：所有资产共用 dvc-cache 存储桶
        return "dvc-cache";
    }

    private String resolveObjectKey(Version version, Artifact artifact) {
        return version.assetId() + "/" + version.version() + "/" + artifact.path();
    }

    /**
     * 下载票据视图。
     */
    public record DownloadTicket(String method, String presignedUrl,
                                 Instant expiresAt, String fileName, Long fileSize) {}
}
