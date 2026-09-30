/*
 * 功能: MinIO 存储适配器，实现真实 S3 Multipart Upload 与对象读写（ops 按桶路由凭据；presign 走公开端点客户端）。
 * 时间: 2026-09-30
 * 作者: AxeXie
 */
package com.aihub.integration.minio.infrastructure;

import com.aihub.transfer.domain.StoragePort;
import io.minio.BucketExistsArgs;
import io.minio.CopyObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.http.Method;
import io.minio.StatObjectArgs;
import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

/**
 * MinIO 存储适配器。
 *
 * <p>实现 {@link StoragePort}：Multipart 走 AWS SDK v2 S3Client；单对象预签名走 MinIO SDK。
 */
@Component
public class MinioStorageAdapter implements StoragePort {

    private static final Logger LOG = LoggerFactory.getLogger(MinioStorageAdapter.class);

    /** DVC 缓存桶：凭据隔离归 DVC scoped 账号，主账号无该桶权限。 */
    static final String DVC_CACHE_BUCKET = "dvc-cache";

    private final MinioClient minioClient;
    private final MinioClient dvcCacheMinioClient;
    private final MinioClient presignMinioClient;
    private final MinioClient presignDvcCacheMinioClient;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    public MinioStorageAdapter(MinioClient minioClient,
                               @Qualifier("dvcCacheMinioClient") MinioClient dvcCacheMinioClient,
                               @Qualifier("presignMinioClient") MinioClient presignMinioClient,
                               @Qualifier("presignDvcCacheMinioClient") MinioClient presignDvcCacheMinioClient,
                               S3Client s3Client,
                               S3Presigner s3Presigner) {
        this.minioClient = minioClient;
        this.dvcCacheMinioClient = dvcCacheMinioClient;
        this.presignMinioClient = presignMinioClient;
        this.presignDvcCacheMinioClient = presignDvcCacheMinioClient;
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    /** 按桶选择 ops 客户端：dvc-cache 走 DVC scoped 凭据，其余走主凭据。 */
    private MinioClient clientFor(String bucketName) {
        return DVC_CACHE_BUCKET.equals(bucketName) ? dvcCacheMinioClient : minioClient;
    }

    /** 按桶选择 presign 客户端：公开端点签名 + 与 ops 一致的主/scoped 凭据分桶。 */
    private MinioClient presignClientFor(String bucketName) {
        return DVC_CACHE_BUCKET.equals(bucketName)
                ? presignDvcCacheMinioClient
                : presignMinioClient;
    }

    @Override
    public boolean bucketExists(String bucketName) {
        try {
            return clientFor(bucketName).bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build());
        } catch (Exception ex) {
            LOG.error("bucketExists failed bucket={}", bucketName, ex);
            throw new StorageException("failed to check bucket existence: " + bucketName, ex);
        }
    }

    @Override
    public void createBucket(String bucketName) {
        try {
            if (!bucketExists(bucketName)) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build());
            }
        } catch (Exception ex) {
            LOG.error("createBucket failed bucket={}", bucketName, ex);
            throw new StorageException("failed to create bucket: " + bucketName, ex);
        }
    }

    @Override
    public String createMultipartUpload(String bucketName, String objectKey, String contentType) {
        try {
            CreateMultipartUploadRequest.Builder builder = CreateMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey);
            if (contentType != null && !contentType.isBlank()) {
                builder.contentType(contentType);
            }
            CreateMultipartUploadResponse response = s3Client.createMultipartUpload(builder.build());
            String uploadId = response.uploadId();
            LOG.info("createMultipartUpload bucket={} key={} uploadId={}", bucketName, objectKey, uploadId);
            return uploadId;
        } catch (Exception ex) {
            LOG.error("createMultipartUpload failed bucket={} key={}", bucketName, objectKey, ex);
            throw new StorageException("failed to create multipart upload", ex);
        }
    }

    @Override
    public URL presignPartUpload(String bucketName, String objectKey, String uploadId,
                                 int partNumber, Duration expiry) {
        try {
            UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .uploadId(uploadId)
                    .partNumber(partNumber)
                    .build();
            UploadPartPresignRequest presignRequest = UploadPartPresignRequest.builder()
                    .signatureDuration(expiry)
                    .uploadPartRequest(uploadPartRequest)
                    .build();
            PresignedUploadPartRequest presigned = s3Presigner.presignUploadPart(presignRequest);
            return presigned.url();
        } catch (Exception ex) {
            LOG.error("presignPartUpload failed bucket={} key={} part={}", bucketName, objectKey, partNumber, ex);
            throw new StorageException("failed to presign part upload", ex);
        }
    }

    @Override
    public void completeMultipartUpload(String bucketName, String objectKey, String uploadId,
                                        List<PartInfo> parts) {
        try {
            List<CompletedPart> completedParts = parts.stream()
                    .sorted(Comparator.comparingInt(PartInfo::partNumber))
                    .map(p -> CompletedPart.builder()
                            .partNumber(p.partNumber())
                            .eTag(stripQuotes(p.etag()))
                            .build())
                    .toList();
            CompleteMultipartUploadRequest request = CompleteMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder()
                            .parts(completedParts)
                            .build())
                    .build();
            s3Client.completeMultipartUpload(request);
            LOG.info("completeMultipartUpload bucket={} key={} parts={}", bucketName, objectKey, parts.size());
        } catch (Exception ex) {
            LOG.error("completeMultipartUpload failed bucket={} key={}", bucketName, objectKey, ex);
            throw new StorageException("failed to complete multipart upload", ex);
        }
    }

    @Override
    public void abortMultipartUpload(String bucketName, String objectKey, String uploadId) {
        try {
            s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .uploadId(uploadId)
                    .build());
            LOG.info("abortMultipartUpload bucket={} key={}", bucketName, objectKey);
        } catch (Exception ex) {
            LOG.warn("abortMultipartUpload failed bucket={} key={}", bucketName, objectKey, ex);
        }
    }

    @Override
    public URL presignDownload(String bucketName, String objectKey, Duration expiry) {
        try {
            String url = presignClientFor(bucketName).getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectKey)
                            .expiry((int) expiry.getSeconds(), TimeUnit.SECONDS)
                            .build());
            return new URL(url);
        } catch (Exception ex) {
            LOG.error("presignDownload failed bucket={} key={}", bucketName, objectKey, ex);
            throw new StorageException("failed to presign download", ex);
        }
    }

    @Override
    public URL presignUpload(String bucketName, String objectKey, String contentType, Duration expiry) {
        try {
            String url = presignClientFor(bucketName).getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucketName)
                            .object(objectKey)
                            .expiry((int) expiry.getSeconds(), TimeUnit.SECONDS)
                            .build());
            return new URL(url);
        } catch (Exception ex) {
            LOG.error("presignUpload failed bucket={} key={}", bucketName, objectKey, ex);
            throw new StorageException("failed to presign upload", ex);
        }
    }

    @Override
    public void deleteObject(String bucketName, String objectKey) {
        try {
            clientFor(bucketName).removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build());
        } catch (Exception ex) {
            LOG.error("deleteObject failed bucket={} key={}", bucketName, objectKey, ex);
            throw new StorageException("failed to delete object", ex);
        }
    }

    @Override
    public InputStream readObject(String bucketName, String objectKey) {
        try {
            return clientFor(bucketName).getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build());
        } catch (Exception ex) {
            LOG.error("readObject failed bucket={} key={}", bucketName, objectKey, ex);
            throw new StorageException("failed to read object", ex);
        }
    }

    @Override
    public Optional<String> sha256Hex(String bucketName, String objectKey) {
        if (!objectExists(bucketName, objectKey)) {
            return Optional.empty();
        }
        try (InputStream in = readObject(bucketName, objectKey)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
            return Optional.of(bytesToHex(digest.digest()));
        } catch (Exception ex) {
            LOG.warn("sha256Hex failed bucket={} key={}", bucketName, objectKey, ex);
            return Optional.empty();
        }
    }

    @Override
    public boolean objectExists(String bucketName, String objectKey) {
        try {
            clientFor(bucketName).statObject(
                    StatObjectArgs.builder().bucket(bucketName).object(objectKey).build());
            return true;
        } catch (Exception ex) {
            // 可诊断性：存在性判定失败（权限/网络等）不得静默——此前 Access Denied 被吞导致
            // 预览代理 404 无法归因（TASK-P0BR-068 §3 BUCKET_CREDENTIALS）。
            LOG.warn("objectExists failed bucket={} key={}: {}", bucketName, objectKey, ex.getMessage());
            return false;
        }
    }

    private static String stripQuotes(String etag) {
        if (etag == null) {
            return "";
        }
        return etag.startsWith("\"") && etag.endsWith("\"") ? etag.substring(1, etag.length() - 1) : etag;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    /** 存储操作异常。 */
    public static class StorageException extends RuntimeException {
        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
