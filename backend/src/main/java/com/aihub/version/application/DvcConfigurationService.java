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
import com.aihub.integration.minio.infrastructure.MinioStsCredentialIssuer;
import com.aihub.version.domain.DvcStorageProperties;
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
    private static final Duration MAX_CREDENTIAL_TTL = Duration.ofHours(1);
    private static final String DVC_CACHE_BUCKET = "dvc-cache";

    private final DvcStorageProperties dvcStorageProperties;
    private final MinioStsCredentialIssuer stsCredentialIssuer;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public DvcConfigurationService(DvcStorageProperties dvcStorageProperties,
                                   MinioStsCredentialIssuer stsCredentialIssuer,
                                   AuthorizationService authorizationService,
                                   AuditService auditService) {
        this.dvcStorageProperties = dvcStorageProperties;
        this.stsCredentialIssuer = stsCredentialIssuer;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    /** 生成 DVC Remote 配置。 */
    public DvcRemoteConfig generateRemoteConfig(String assetId) {
        authorizationService.requirePermission("asset:read");

        String endpoint = dvcStorageProperties.getExternalEndpoint() != null
                ? dvcStorageProperties.getExternalEndpoint()
                : dvcStorageProperties.getEndpoint();

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
     * <p>优先尝试 MinIO STS AssumeRole；不可用时回退 scoped 配置密钥（expireAt ≤ 1h）。
     * 永不返回平台 root 密钥。
     */
    public DvcCredentials issueCredentials(String assetId) {
        authorizationService.requirePermission("asset:read");

        Instant expiresAt = Instant.now().plus(CREDENTIAL_TTL);
        var stsResult = stsCredentialIssuer.issue(assetId, DVC_CACHE_BUCKET, CREDENTIAL_TTL);
        if (stsResult.isPresent()) {
            MinioStsCredentialIssuer.StsCredentials sts = stsResult.get();
            Instant maxExpiry = Instant.now().plus(MAX_CREDENTIAL_TTL);
            expiresAt = sts.expiresAt().isBefore(maxExpiry) ? sts.expiresAt() : maxExpiry;
            DvcCredentials credentials = new DvcCredentials(
                    sts.accessKeyId(),
                    sts.secretAccessKey(),
                    expiresAt,
                    DVC_CACHE_BUCKET,
                    assetId + "/",
                    "STS_ASSUME_ROLE",
                    null,
                    sts.sessionToken());
            auditCredentials(assetId, credentials);
            LOG.info("issued DVC STS credentials for asset={} expiresAt={}", assetId, expiresAt);
            return credentials;
        }

        String accessKey = dvcStorageProperties.resolveDvcAccessKey();
        String secretKey = dvcStorageProperties.resolveDvcSecretKey();
        if (accessKey == null || secretKey == null) {
            throw new ValidationException(
                    "DVC scoped credentials not configured; set aihub.minio.dvc-access-key and dvc-secret-key");
        }

        DvcCredentials credentials = new DvcCredentials(
                accessKey,
                secretKey,
                expiresAt,
                DVC_CACHE_BUCKET,
                assetId + "/",
                "SCOPED_CONFIG_KEY",
                "MinIO STS not integrated; time-limited scoped key from config with expireAt metadata",
                null);

        auditCredentials(assetId, credentials);
        LOG.info("issued DVC scoped credentials for asset={} expiresAt={} type={}",
                assetId, expiresAt, credentials.credentialType());
        return credentials;
    }

    private void auditCredentials(String assetId, DvcCredentials credentials) {
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
                        "expiresAt", credentials.expiresAt().toString(),
                        "bucket", DVC_CACHE_BUCKET,
                        "credentialType", credentials.credentialType(),
                        "accessKeyId", credentials.accessKey())));
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
                                 String credentialType, String limitationNote,
                                 String sessionToken) {}
}
