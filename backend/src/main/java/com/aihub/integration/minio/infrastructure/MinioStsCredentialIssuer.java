/*
 * 功能: MinIO STS 短期凭据签发器——尝试 AssumeRole，失败时由上层回退 scoped 配置密钥。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.integration.minio.infrastructure;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;

/**
 * MinIO STS 凭据签发器。
 *
 * <p>使用服务端管理员密钥调用 MinIO 兼容 STS AssumeRole，签发 scoped 短期凭据。
 * 管理员密钥仅用于 STS 调用，永不返回给客户端。MinIO 未启用 STS 时返回 empty。
 */
@Component
public class MinioStsCredentialIssuer {

    private static final Logger LOG = LoggerFactory.getLogger(MinioStsCredentialIssuer.class);
    private static final Duration MAX_SESSION = Duration.ofHours(1);
    private static final String SESSION_ROLE_ARN = "arn:aws:iam::minio:role/dvc-scoped";

    private final MinioProperties minioProperties;

    public MinioStsCredentialIssuer(MinioProperties minioProperties) {
        this.minioProperties = minioProperties;
    }

    /**
     * 尝试为资产前缀签发 STS 短期凭据。
     */
    public Optional<StsCredentials> issue(String assetId, String bucket, Duration requestedTtl) {
        String endpoint = minioProperties.getEndpoint();
        String adminAccessKey = minioProperties.getAccessKey();
        String adminSecretKey = minioProperties.getSecretKey();
        if (endpoint == null || endpoint.isBlank()
                || adminAccessKey == null || adminSecretKey == null) {
            return Optional.empty();
        }

        Duration ttl = requestedTtl.compareTo(MAX_SESSION) > 0 ? MAX_SESSION : requestedTtl;
        String prefix = assetId + "/";
        String policy = """
                {
                  "Version": "2012-10-17",
                  "Statement": [{
                    "Effect": "Allow",
                    "Action": ["s3:GetObject", "s3:PutObject", "s3:DeleteObject", "s3:ListBucket"],
                    "Resource": [
                      "arn:aws:s3:::%s/%s*",
                      "arn:aws:s3:::%s"
                    ],
                    "Condition": {
                      "StringLike": { "s3:prefix": ["%s*"] }
                    }
                  }]
                }
                """.formatted(bucket, prefix, bucket, prefix);

        try (StsClient stsClient = StsClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(adminAccessKey, adminSecretKey)))
                .build()) {

            AssumeRoleResponse response = stsClient.assumeRole(AssumeRoleRequest.builder()
                    .roleArn(SESSION_ROLE_ARN)
                    .roleSessionName("dvc-" + assetId)
                    .durationSeconds((int) ttl.toSeconds())
                    .policy(policy)
                    .build());

            Credentials credentials = response.credentials();
            return Optional.of(new StsCredentials(
                    credentials.accessKeyId(),
                    credentials.secretAccessKey(),
                    credentials.sessionToken(),
                    credentials.expiration()));
        } catch (Exception ex) {
            LOG.debug("MinIO STS AssumeRole unavailable for asset={}: {}", assetId, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * STS 短期凭据。
     */
    public record StsCredentials(String accessKeyId,
                                 String secretAccessKey,
                                 String sessionToken,
                                 Instant expiresAt) {
    }
}
