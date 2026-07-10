/*
 * 功能: DVC 配置服务——签发 scoped 短期凭据，不暴露平台 root 密钥。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.version.application;

import com.aihub.audit.application.AuditEvent;
import com.aihub.audit.application.AuditService;
import com.aihub.audit.domain.AuditResult;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.integration.minio.infrastructure.MinioProperties;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.identity.PrincipalContextHolder;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * DVC 配置服务。
 */
@Service
public class DvcConfigurationService {

    private static final Logger LOG = LoggerFactory.getLogger(DvcConfigurationService.class);
    private static final Duration CREDENTIAL_TTL = Duration.ofMinutes(15);
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

    /** 生成 DVC Remote 配置。 */
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
                principalId(),
                principalType(),
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
     * <p>使用 {@code aihub.minio.dvc-access-key} / {@code dvc-secret-key} 作用域密钥；
     * 未配置时 fail-closed，不返回平台 root 密钥。MinIO STS AssumeRole 待后续集成。
     */
    public DvcCredentials issueCredentials(String assetId) {
        authorizationService.requirePermission("asset:read");

        String accessKey = minioProperties.resolveDvcAccessKey();
        String secretKey = minioProperties.resolveDvcSecretKey();
        if (accessKey == null || secretKey == null) {
            throw new ValidationException(
                    "DVC scoped credentials not configured; set aihub.minio.dvc-access-key and dvc-secret-key");
        }

        Instant expiresAt = Instant.now().plus(CREDENTIAL_TTL);
        DvcCredentials credentials = new DvcCredentials(
                accessKey,
                secretKey,
                expiresAt,
                DVC_CACHE_BUCKET,
                assetId + "/",
                "SCOPED_CONFIG_KEY",
                "MinIO STS not integrated; time-limited scoped key from config with expireAt metadata");

        auditService.record(new AuditEvent(
                "DVC_CREDENTIALS_ISSUED",
                "dvc:credentials",
                principalId(),
                principalType(),
                "ASSET",
                assetId,
                null,
                null,
                AuditResult.SUCCEEDED,
                null,
                Map.of(
                        "expiresAt", expiresAt.toString(),
                        "bucket", DVC_CACHE_BUCKET,
                        "credentialType", credentials.credentialType(),
                        "accessKeyId", accessKey)));

        LOG.info("issued DVC scoped credentials for asset={} expiresAt={} type={}",
                assetId, expiresAt, credentials.credentialType());
        return credentials;
    }

    private static String principalId() {
        return PrincipalContextHolder.current().map(c -> c.principalId()).orElse(null);
    }

    private static String principalType() {
        return PrincipalContextHolder.current()
                .map(c -> c.principalType() == null ? null : c.principalType().name())
                .orElse(null);
    }

    public record DvcRemoteConfig(String bucket, String endpoint, String type,
                                  String prefix, Instant expiresAt) {}

    public record DvcCredentials(String accessKey, String secretKey,
                                 Instant expiresAt, String bucket, String prefix,
                                 String credentialType, String limitationNote) {}
}
