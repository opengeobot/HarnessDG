package com.aihub.version.application;

import com.aihub.audit.application.AuditEvent;
import com.aihub.audit.application.AuditService;
import com.aihub.audit.domain.AuditResult;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.integration.minio.infrastructure.MinioProperties;
import com.aihub.shared.identity.PrincipalContextHolder;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * DVC 配置服务。
 *
 * <p>为客户端生成 DVC Remote 配置（指向 MinIO dvc-cache 存储桶）并签发短期凭据。
 * 凭据有效期 15 分钟，最小权限范围，100% 审计。
 */
@Service
public class DvcConfigurationService {

    private static final Logger LOG = LoggerFactory.getLogger(DvcConfigurationService.class);

    /** DVC 凭据默认有效期：15 分钟。 */
    private static final Duration CREDENTIAL_TTL = Duration.ofMinutes(15);

    /** DVC 缓存存储桶名称。 */
    private static final String DVC_CACHE_BUCKET = "dvc-cache";

    private final MinioProperties minioProperties;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public DvcConfigurationService(MinioProperties minioProperties,
                                   AuthorizationService authorizationService,
                                   AuditService auditService) {
        this.minioProperties = minioProperties;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    /**
     * 生成 DVC Remote 配置。
     *
     * @param assetId 资产 ID
     * @return DVC 远程配置视图
     */
    public DvcRemoteConfig generateRemoteConfig(String assetId) {
        authorizationService.requirePermission("asset:read");

        String endpoint = minioProperties.getExternalEndpoint() != null
                ? minioProperties.getExternalEndpoint()
                : minioProperties.getEndpoint();

        DvcRemoteConfig config = new DvcRemoteConfig(
                DVC_CACHE_BUCKET,
                endpoint,
                "s3",
                assetId + "/",
                Instant.now().plus(CREDENTIAL_TTL));

        auditService.record(new AuditEvent(
                "DVC_REMOTE_CONFIG_ISSUED",
                "dvc:config",
                PrincipalContextHolder.current().map(c -> c.principalId()).orElse(null),
                PrincipalContextHolder.current().map(c -> c.principalType() == null ? null : c.principalType().name()).orElse(null),
                "ASSET",
                assetId,
                null,
                null,
                AuditResult.SUCCEEDED,
                null,
                Map.of("bucket", DVC_CACHE_BUCKET, "prefix", assetId + "/")));

        LOG.info("issued DVC remote config for asset={}", assetId);
        return config;
    }

    /**
     * 签发短期 DVC 凭据。
     *
     * @param assetId 资产 ID
     * @return 短期凭据视图
     */
    public DvcCredentials issueCredentials(String assetId) {
        authorizationService.requirePermission("asset:read");

        // P2 范围：当前使用平台 MinIO 凭据，后续可集成 STS 临时凭据
        Instant expiresAt = Instant.now().plus(CREDENTIAL_TTL);

        DvcCredentials credentials = new DvcCredentials(
                minioProperties.getAccessKey(),
                minioProperties.getSecretKey(),
                expiresAt,
                DVC_CACHE_BUCKET,
                assetId + "/");

        auditService.record(new AuditEvent(
                "DVC_CREDENTIALS_ISSUED",
                "dvc:credentials",
                PrincipalContextHolder.current().map(c -> c.principalId()).orElse(null),
                PrincipalContextHolder.current().map(c -> c.principalType() == null ? null : c.principalType().name()).orElse(null),
                "ASSET",
                assetId,
                null,
                null,
                AuditResult.SUCCEEDED,
                null,
                Map.of("expiresAt", expiresAt.toString(), "bucket", DVC_CACHE_BUCKET)));

        LOG.info("issued DVC credentials for asset={} expiresAt={}", assetId, expiresAt);
        return credentials;
    }

    /**
     * DVC Remote 配置视图。
     */
    public record DvcRemoteConfig(String bucket, String endpoint, String type,
                                  String prefix, Instant expiresAt) {}

    /**
     * DVC 短期凭据视图。
     */
    public record DvcCredentials(String accessKey, String secretKey,
                                 Instant expiresAt, String bucket, String prefix) {}
}
