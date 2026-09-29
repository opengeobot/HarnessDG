/*
 * 功能: MinIO / AWS S3 客户端配置（主凭据客户端 + dvc-cache scoped 凭据客户端）。
 * 时间: 2026-09-29
 * 作者: AxeXie
 */
package com.aihub.integration.minio.infrastructure;

import io.minio.MinioClient;
import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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

    @Bean
    public S3Presigner s3Presigner(MinioProperties properties) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                properties.getAccessKey(), properties.getSecretKey());
        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();
        return S3Presigner.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(s3Config)
                .build();
    }
}
