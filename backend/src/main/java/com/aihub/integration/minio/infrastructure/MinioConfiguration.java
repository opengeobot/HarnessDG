/*
 * 功能: MinIO / AWS S3 客户端配置（ops 内网客户端 + presign 公开端点客户端；主凭据 + dvc-cache scoped 凭据）。
 * 时间: 2026-09-30
 * 作者: AxeXie
 */
package com.aihub.integration.minio.infrastructure;

import io.minio.MinioClient;
import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * MinIO 客户端配置。
 */
@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfiguration {

    @Bean
    @Primary
    public MinioClient minioClient(MinioProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }

    /**
     * dvc-cache 专用客户端：使用 DVC scoped 凭据（凭据隔离，主账号无该桶权限）。
     *
     * <p>未配置 DVC 凭据时返回主客户端实例（兼容本地单一全权凭据场景），
     * 绝不回退为半套凭据。
     */
    @Bean(name = "dvcCacheMinioClient")
    public MinioClient dvcCacheMinioClient(MinioProperties properties, MinioClient minioClient) {
        String dvcAccessKey = properties.resolveDvcAccessKey();
        String dvcSecretKey = properties.resolveDvcSecretKey();
        if (dvcAccessKey == null || dvcSecretKey == null) {
            return minioClient;
        }
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(dvcAccessKey, dvcSecretKey)
                .build();
    }

    /**
     * 预签名签发端点（REQ-UPL-002 L172「URL 使用公开 MinIO Endpoint」）：
     * {@code externalEndpoint} 非空白则用之，否则回退内网 {@code endpoint}
     * （worker 等无浏览器消费面的进程保持原行为，装配安全）。
     *
     * <p>S3 SigV4 canonical request 含 Host，签发端点必须与浏览器实际访问端点
     * 一致，故 presign 客户端独立于 ops 客户端构建（TASK-P0BR-070 F-6）。
     */
    static String resolvePresignEndpoint(MinioProperties properties) {
        return StringUtils.hasText(properties.getExternalEndpoint())
                ? properties.getExternalEndpoint().trim()
                : properties.getEndpoint();
    }

    /** 主凭据 presign 客户端：以公开端点签发 GET/PUT 预签名 URL。 */
    @Bean(name = "presignMinioClient")
    public MinioClient presignMinioClient(MinioProperties properties) {
        return MinioClient.builder()
                .endpoint(resolvePresignEndpoint(properties))
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }

    /** dvc-cache scoped 凭据 presign 客户端；未配置 DVC 凭据时回退主 presign 客户端。 */
    @Bean(name = "presignDvcCacheMinioClient")
    public MinioClient presignDvcCacheMinioClient(MinioProperties properties,
                                                  MinioClient presignMinioClient) {
        String dvcAccessKey = properties.resolveDvcAccessKey();
        String dvcSecretKey = properties.resolveDvcSecretKey();
        if (dvcAccessKey == null || dvcSecretKey == null) {
            return presignMinioClient;
        }
        return MinioClient.builder()
                .endpoint(resolvePresignEndpoint(properties))
                .credentials(dvcAccessKey, dvcSecretKey)
                .build();
    }

    @Bean
    public S3Client s3Client(MinioProperties properties) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                properties.getAccessKey(), properties.getSecretKey());
        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();
        return S3Client.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(s3Config)
                .build();
    }

    /** Multipart Part 预签名签发器：以公开端点签名（浏览器可达，REQ-UPL-002 L172）。 */
    @Bean
    public S3Presigner s3Presigner(MinioProperties properties) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                properties.getAccessKey(), properties.getSecretKey());
        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();
        return S3Presigner.builder()
                .endpointOverride(URI.create(resolvePresignEndpoint(properties)))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(s3Config)
                .build();
    }
}
