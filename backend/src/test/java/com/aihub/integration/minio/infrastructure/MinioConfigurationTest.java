/*
 * 功能: MinioConfiguration 单元测试（presign 公开端点解析与客户端回退语义）。
 * 时间: 2026-09-30
 * 作者: AxeXie
 */
package com.aihub.integration.minio.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.minio.MinioClient;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

/**
 * presign 端点接线口径（TASK-P0BR-070）：resolvePresignEndpoint 纯函数行为 +
 * S3Presigner 使用公开端点签名 + presignDvcCache 回退语义。
 * MinioClient 内部端点绑定由 E4 层 e2e 的真实 URL host 断言守护。
 */
class MinioConfigurationTest {

    private final MinioConfiguration configuration = new MinioConfiguration();

    private static MinioProperties properties(String endpoint, String externalEndpoint) {
        MinioProperties properties = new MinioProperties();
        properties.setEndpoint(endpoint);
        properties.setExternalEndpoint(externalEndpoint);
        return properties;
    }

    @Test
    void resolvePresignEndpointPrefersExternalWhenConfigured() {
        MinioProperties props = properties("http://minio:9000", "http://localhost:9000");

        assertThat(MinioConfiguration.resolvePresignEndpoint(props))
                .isEqualTo("http://localhost:9000");
    }

    @Test
    void resolvePresignEndpointFallsBackToInternalWhenBlank() {
        // worker 语义：未注入公开端点 ⇒ 回退内网（TASK-P0BR-070 §3.3）
        assertThat(MinioConfiguration.resolvePresignEndpoint(properties("http://minio:9000", null)))
                .isEqualTo("http://minio:9000");
        assertThat(MinioConfiguration.resolvePresignEndpoint(properties("http://minio:9000", "")))
                .isEqualTo("http://minio:9000");
        assertThat(MinioConfiguration.resolvePresignEndpoint(properties("http://minio:9000", "   ")))
                .isEqualTo("http://minio:9000");
    }

    @Test
    void s3PresignerSignsPartUploadOnPublicEndpoint() {
        MinioProperties props = properties("http://minio:9000", "http://localhost:9000");
        S3Presigner presigner = configuration.s3Presigner(props);

        UploadPartPresignRequest request = UploadPartPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .uploadPartRequest(UploadPartRequest.builder()
                        .bucket("asset-staging")
                        .key("staging/key")
                        .uploadId("upload-abc")
                        .partNumber(1)
                        .build())
                .build();
        PresignedUploadPartRequest presigned = presigner.presignUploadPart(request);

        String host = presigned.url().getHost();
        int port = presigned.url().getPort();
        assertThat(host + ":" + port).isEqualTo("localhost:9000");
    }

    @Test
    void s3PresignerFallsBackToInternalWhenExternalEndpointAbsent() {
        MinioProperties props = properties("http://minio:9000", "");
        S3Presigner presigner = configuration.s3Presigner(props);

        UploadPartPresignRequest request = UploadPartPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .uploadPartRequest(UploadPartRequest.builder()
                        .bucket("asset-staging")
                        .key("staging/key")
                        .uploadId("upload-abc")
                        .partNumber(1)
                        .build())
                .build();
        PresignedUploadPartRequest presigned = presigner.presignUploadPart(request);

        assertThat(presigned.url().getHost()).isEqualTo("minio");
    }

    @Test
    void presignClientsAreNonNull() {
        MinioProperties props = properties("http://minio:9000", "http://localhost:9000");

        MinioClient presign = configuration.presignMinioClient(props);
        assertThat(presign).isNotNull();

        MinioClient scoped = configuration.presignDvcCacheMinioClient(props, presign);
        // 无 DVC 凭据 ⇒ 同实例回退（与 dvcCacheMinioClient 模式同构）
        assertThat(scoped).isSameAs(presign);
    }

    @Test
    void presignDvcCacheReturnsDistinctClientWhenScopedCredentialsConfigured() {
        MinioProperties props = properties("http://minio:9000", "http://localhost:9000");
        props.setDvcAccessKey("dvc-ak");
        props.setDvcSecretKey("dvc-sk");
        MinioClient primaryPresign = configuration.presignMinioClient(props);

        MinioClient scoped = configuration.presignDvcCacheMinioClient(props, primaryPresign);

        assertThat(scoped).isNotSameAs(primaryPresign);
    }
}
