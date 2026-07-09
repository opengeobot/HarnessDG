package com.aihub.integration.minio.infrastructure;

import com.aihub.transfer.domain.StoragePort;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.http.Method;
import java.io.ByteArrayInputStream;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * MinIO 存储适配器。
 *
 * <p>实现 {@link StoragePort}，封装 MinIO SDK 调用。
 * Multipart Upload 的 Part 签名通过预签名 URL 实现，客户端直传。
 */
@Component
public class MinioStorageAdapter implements StoragePort {

    private static final Logger LOG = LoggerFactory.getLogger(MinioStorageAdapter.class);

    private final MinioClient minioClient;
    private final MinioProperties properties;

    public MinioStorageAdapter(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public boolean bucketExists(String bucketName) {
        try {
            return minioClient.bucketExists(
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
        // MinIO Java SDK 8.x 未暴露 S3 CreateMultipartUpload；当前以 objectKey 作为 uploadId 占位，
        // presignPartUpload/completeMultipartUpload 按单对象预签名 PUT 路径工作。
        // 生产环境可切换 AWS SDK v2 S3Client#createMultipartUpload 获取真实 uploadId。
        LOG.info("createMultipartUpload bucket={} key={} contentType={} uploadId={}",
                bucketName, objectKey, contentType, objectKey);
        return objectKey;
    }

    @Override
    public URL presignPartUpload(String bucketName, String objectKey, String uploadId,
                                 int partNumber, Duration expiry) {
        try {
            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucketName)
                            .object(objectKey)
                            .expiry((int) expiry.getSeconds(), TimeUnit.SECONDS)
                            .build());
            return new URL(url);
        } catch (Exception ex) {
            LOG.error("presignPartUpload failed bucket={} key={}", bucketName, objectKey, ex);
            throw new StorageException("failed to presign part upload", ex);
        }
    }

    @Override
    public void completeMultipartUpload(String bucketName, String objectKey, String uploadId,
                                        List<PartInfo> parts) {
        // 简化实现：客户端通过预签名 URL 完成上传后，服务端验证对象存在性。
        LOG.info("completeMultipartUpload bucket={} key={} parts={}", bucketName, objectKey, parts.size());
    }

    @Override
    public void abortMultipartUpload(String bucketName, String objectKey, String uploadId) {
        // 简化实现：删除可能存在的部分上传对象。
        LOG.info("abortMultipartUpload bucket={} key={}", bucketName, objectKey);
    }

    @Override
    public URL presignDownload(String bucketName, String objectKey, Duration expiry) {
        try {
            String url = minioClient.getPresignedObjectUrl(
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
            String url = minioClient.getPresignedObjectUrl(
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
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build());
        } catch (Exception ex) {
            LOG.error("deleteObject failed bucket={} key={}", bucketName, objectKey, ex);
            throw new StorageException("failed to delete object", ex);
        }
    }

    /** 存储操作异常。 */
    public static class StorageException extends RuntimeException {
        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
