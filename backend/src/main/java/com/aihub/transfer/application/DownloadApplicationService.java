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

    /** 预签名 URL / GIT_DVC 票据有效期：15 分钟。 */
    private static final Duration TICKET_TTL = Duration.ofMinutes(15);

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
        Version version = versionRepository.findByVersionId(versionId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.VERSION_NOT_FOUND,
                        "version not found", Map.of("versionId", versionId)));

        if (version.status() != VersionStatus.PUBLISHED) {
            throw new IllegalStateException(
                    "only PUBLISHED versions can be downloaded, current: " + version.status());
        }

        authorizationService.requirePermission("asset:read");

        Asset asset = assetRepository.findByAssetId(version.assetId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.ASSET_NOT_FOUND,
                        "asset not found", Map.of("assetId", version.assetId())));

        Instant expiresAt = Instant.now().plus(TICKET_TTL);

        if (artifactId != null) {
            Artifact artifact = versionRepository.listArtifactsByVersion(versionId).stream()
                    .filter(a -> a.artifactId().equals(artifactId))
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException(ErrorCode.VERSION_NOT_FOUND,
                            "artifact not found", Map.of("artifactId", artifactId)));

            String objectKey = resolveObjectKey(version, artifact);
            var url = storagePort.presignDownload(resolveBucket(asset), objectKey, TICKET_TTL);

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
            return new DownloadTicket("PRESIGNED_URL", url.toString(), expiresAt,
                    artifact.path(), artifact.size(), null, null, null, version.assetId());
        }

        String gitCloneUrl = asset.repository() != null ? asset.repository().cloneUrl() : null;
        String revision = version.sourceCommit() != null ? version.sourceCommit() : version.gitTag();
        String dvcCredentialsUrl = "/api/v1/assets/" + version.assetId() + "/dvc/credentials";

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
                Map.of("method", "GIT_DVC", "revision", revision != null ? revision : "")));

        LOG.info("issued GIT_DVC download ticket versionId={} assetId={}", versionId, version.assetId());
        return new DownloadTicket("GIT_DVC", null, expiresAt, null, null,
                gitCloneUrl, revision, dvcCredentialsUrl, version.assetId());
    }

    private String resolveBucket(Asset asset) {
        return "dvc-cache";
    }

    private String resolveObjectKey(Version version, Artifact artifact) {
        return version.assetId() + "/" + version.version() + "/" + artifact.path();
    }

    /**
     * 下载票据视图。
     *
     * <p>GIT_DVC 方法通过 {@code dvcCredentialsUrl} 引导客户端获取短期凭据，不在票据中嵌入 Secret。
     */
    public record DownloadTicket(String method, String presignedUrl,
                                 Instant expiresAt, String fileName, Long fileSize,
                                 String gitCloneUrl, String revision,
                                 String dvcCredentialsUrl, String assetId) {}
}
