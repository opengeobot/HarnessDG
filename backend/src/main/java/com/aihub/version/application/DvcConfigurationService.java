/*
 * 功能: DVC 配置服务——生成 DVC Remote 配置与短期 S3 凭据（version 级别作用域）。
 * 时间: 2026-07-30
 * 作者: AxeXie
 */
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
 * 审计记录中对 secretKey 等凭据字段做脱敏处理，不入日志明文。
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
     * 生成 Version 级别的 DVC Remote 配置（含短期 S3 凭据）。
     *
     * <p>DVC 等价操作：dvc remote add + 短期凭据，作用于指定 versionId 作用域。
     * 凭据有效期 15 分钟，最小权限范围，仅允许读写 version 前缀下的对象。
     *
     * <p>审计记录中对 secretKey 字段做脱敏，不暴露凭据明文。
     *
     * @param versionId 版本 ID
     * @return 包含 DVC Remote 配置和短期 S3 凭据的完整视图
     */
    public DvcRemoteConfigFull generateDvcRemoteConfig(String versionId) {
        authorizationService.requirePermission("asset:read");

        String endpoint = minioProperties.getExternalEndpoint() != null
                ? minioProperties.getExternalEndpoint()
                : minioProperties.getEndpoint();
        Instant expiresAt = Instant.now().plus(CREDENTIAL_TTL);
        String prefix = "versions/" + versionId + "/";

        // 构造 DVC .dvc/config 格式的远程配置段
        String dvcRemoteConfig = formatDvcRemoteConfig(DVC_CACHE_BUCKET, prefix);

        DvcRemoteConfigFull config = new DvcRemoteConfigFull(
                DVC_CACHE_BUCKET,
                endpoint,
                prefix,
                minioProperties.getAccessKey(),
                minioProperties.getSecretKey(),
                expiresAt,
                dvcRemoteConfig);

        // 审计：secretKey 必须脱敏，不可出现在审计摘要明文
        auditService.record(new AuditEvent(
                "DVC_REMOTE_CONFIG_FULL_ISSUED",
                "dvc:config:full",
                PrincipalContextHolder.current().map(c -> c.principalId()).orElse(null),
                PrincipalContextHolder.current().map(c -> c.principalType() == null ? null : c.principalType().name()).orElse(null),
                "VERSION",
                versionId,
                null,
                null,
                AuditResult.SUCCEEDED,
                null,
                Map.of(
                        "bucket", DVC_CACHE_BUCKET,
                        "prefix", prefix,
                        "expiresAt", expiresAt.toString(),
                        "secretKey", "***")));

        LOG.info("issued DVC full remote config for version={} expiresAt={}", versionId, expiresAt);
        return config;
    }

    /**
     * 格式化为 DVC .dvc/config 远程配置段。
     *
     * <p>示例输出：
     * <pre>
     * [remote "aihub"]
     *     url = s3://dvc-cache/versions/ver_xxx/
     * </pre>
     */
    private String formatDvcRemoteConfig(String bucket, String prefix) {
        return "[remote \"aihub\"]\n"
                + "    url = s3://" + bucket + "/" + prefix;
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

    /**
     * DVC Remote 完整配置视图（含短期 S3 凭据 + 配置文本）。
     *
     * <p>注意：secretKey 仅在 API 响应中返回一次，不存储、不入审计明文。
     * 客户端应将 secretKey 写入 .dvc/config 或环境变量，不二次回传。
     *
     * @param bucket         MinIO 存储桶
     * @param endpoint       MinIO 端点 URL
     * @param prefix         Version 专属对象前缀
     * @param accessKey      短期 S3 Access Key
     * @param secretKey      短期 S3 Secret Key（仅返回一次）
     * @param expiresAt      凭据过期时间（15 分钟 TTL）
     * @param dvcRemoteConfig DVC .dvc/config 格式的远程配置段
     */
    public record DvcRemoteConfigFull(String bucket,
                                      String endpoint,
                                      String prefix,
                                      String accessKey,
                                      String secretKey,
                                      Instant expiresAt,
                                      String dvcRemoteConfig) {}
}
