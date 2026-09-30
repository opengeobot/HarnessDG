/*
 * 功能: MinioStorageAdapter 单元测试（StoragePort 契约；ops/presign 客户端分桶路由）。
 * 时间: 2026-09-30
 * 作者: AxeXie
 */
package com.aihub.integration.minio.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aihub.transfer.domain.StoragePort;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

@ExtendWith(MockitoExtension.class)
class MinioStorageAdapterTest {

    @Mock private MinioClient minioClient;
    @Mock private MinioClient dvcCacheMinioClient;
    @Mock private MinioClient presignMinioClient;
    @Mock private MinioClient presignDvcCacheMinioClient;
    @Mock private S3Client s3Client;
    @Mock private S3Presigner s3Presigner;

    private MinioStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new MinioStorageAdapter(minioClient, dvcCacheMinioClient,
                presignMinioClient, presignDvcCacheMinioClient, s3Client, s3Presigner);
    }

    @Test
    void createMultipartUploadReturnsRealUploadId() {
        when(s3Client.createMultipartUpload(any(software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest.class)))
                .thenReturn(CreateMultipartUploadResponse.builder().uploadId("upload-abc").build());

        String uploadId = adapter.createMultipartUpload("asset-staging", "staging/key", "application/octet-stream");

        assertThat(uploadId).isEqualTo("upload-abc");
    }

    @Test
    void presignPartUploadUsesS3Presigner() throws Exception {
        PresignedUploadPartRequest presigned = mock(PresignedUploadPartRequest.class);
        when(presigned.url()).thenReturn(new URL("https://minio.example/presigned"));
        when(s3Presigner.presignUploadPart(any(UploadPartPresignRequest.class))).thenReturn(presigned);

        URL url = adapter.presignPartUpload("asset-staging", "key", "upload-abc", 1, Duration.ofMinutes(5));

        assertThat(url.toString()).contains("minio.example");
    }

    @Test
    void completeMultipartUploadCallsS3Client() {
        when(s3Client.completeMultipartUpload(any(software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest.class)))
                .thenReturn(CompleteMultipartUploadResponse.builder().build());

        adapter.completeMultipartUpload("asset-staging", "key", "upload-abc",
                List.of(new StoragePort.PartInfo(1, "\"etag1\"")));

        verify(s3Client).completeMultipartUpload(any(software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest.class));
    }

    @Test
    void objectExistsForDvcCacheUsesScopedClient() throws Exception {
        assertThat(adapter.objectExists("dvc-cache", "4b/783c07a09e2e8a21a69009d3be7b9b")).isTrue();

        verify(dvcCacheMinioClient).statObject(any(StatObjectArgs.class));
        verify(minioClient, never()).statObject(any(StatObjectArgs.class));
    }

    @Test
    void objectExistsForStagingUsesPrimaryClient() throws Exception {
        assertThat(adapter.objectExists("asset-staging", "staging/key")).isTrue();

        verify(minioClient).statObject(any(StatObjectArgs.class));
        verify(dvcCacheMinioClient, never()).statObject(any(StatObjectArgs.class));
    }

    @Test
    void readObjectForDvcCacheUsesScopedClient() throws Exception {
        when(dvcCacheMinioClient.getObject(any(GetObjectArgs.class)))
                .thenReturn(mock(GetObjectResponse.class));

        assertThat(adapter.readObject("dvc-cache", "4b/abc")).isNotNull();

        verify(dvcCacheMinioClient).getObject(any(GetObjectArgs.class));
        verify(minioClient, never()).getObject(any(GetObjectArgs.class));
    }

    @Test
    void objectExistsFailureIsNotSilent() throws Exception {
        when(minioClient.statObject(any(StatObjectArgs.class)))
                .thenThrow(new RuntimeException("Access Denied"));

        assertThat(adapter.objectExists("asset-staging", "missing")).isFalse();
    }

    @Test
    void presignDownloadForStagingUsesPublicEndpointClient() throws Exception {
        when(presignMinioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://localhost:9000/asset-staging/key?X-Amz-Signature=x");

        URL url = adapter.presignDownload("asset-staging", "key", Duration.ofMinutes(15));

        assertThat(url.getHost()).isEqualTo("localhost");
        verify(presignMinioClient).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
        verify(minioClient, never()).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
    }

    @Test
    void presignDownloadForDvcCacheKeepsScopedCredentials() throws Exception {
        when(presignDvcCacheMinioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://localhost:9000/dvc-cache/key?X-Amz-Signature=x");

        URL url = adapter.presignDownload("dvc-cache", "4b/abc", Duration.ofMinutes(15));

        assertThat(url).isNotNull();
        // 凭据隔离（TASK-P0BR-068 口径）：dvc-cache 的签名同样不得走主凭据客户端
        verify(presignDvcCacheMinioClient).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
        verify(presignMinioClient, never()).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
    }

    @Test
    void presignUploadUsesPublicEndpointClient() throws Exception {
        when(presignMinioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://localhost:9000/asset-staging/key?X-Amz-Signature=x");

        URL url = adapter.presignUpload("asset-staging", "key", "application/octet-stream",
                Duration.ofMinutes(15));

        assertThat(url.getPort()).isEqualTo(9000);
        verify(presignMinioClient).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
        verify(minioClient, never()).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
    }

    @Test
    void opsReadPathStaysOnInternalEndpointClient() throws Exception {
        // presign 切换不得影响 ops 路径：staging 的 stat 仍走主内网客户端
        assertThat(adapter.objectExists("asset-staging", "staging/key")).isTrue();

        verify(minioClient).statObject(any(StatObjectArgs.class));
        verify(presignMinioClient, never()).statObject(any(StatObjectArgs.class));
    }

    @Test
    void presignPartUploadExpiryIsPassedThrough() {
        PresignedUploadPartRequest presigned = mock(PresignedUploadPartRequest.class);
        when(s3Presigner.presignUploadPart(any(UploadPartPresignRequest.class))).thenReturn(presigned);

        adapter.presignPartUpload("asset-staging", "key", "upload-abc", 1, Duration.ofMinutes(15));

        ArgumentCaptor<UploadPartPresignRequest> captor =
                ArgumentCaptor.forClass(UploadPartPresignRequest.class);
        verify(s3Presigner).presignUploadPart(captor.capture());
        assertThat(captor.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(15));
    }
}
