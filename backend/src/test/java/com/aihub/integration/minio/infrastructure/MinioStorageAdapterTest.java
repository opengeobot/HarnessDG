/*
 * 功能: MinioStorageAdapter 单元测试（StoragePort 契约）。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.integration.minio.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aihub.transfer.domain.StoragePort;
import io.minio.MinioClient;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    @Mock private S3Client s3Client;
    @Mock private S3Presigner s3Presigner;

    private MinioStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new MinioStorageAdapter(minioClient, s3Client, s3Presigner);
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
}
