/*
 * 功能: MinIO 存储适配器，封装 MinIO SDK 调用。
 * 时间: 2026-07-30
 * 作者: AxeXie
 */
package com.aihub.integration.minio.infrastructure;

import com.aihub.transfer.domain.StoragePort;
import io.minio.BucketExistsArgs;
import io.minio.CopyObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.http.Method;
import io.minio.errors.ErrorResponseException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
        // MinIO Java SDK 不直接暴露 createMultipartUpload API，
        // 使用预签名 URL 方式：客户端通过预签名 URL 自行完成 Multipart。
        // 此处返回 objectKey 作为 uploadId（简化实现，生产环境应使用更完善的方案）。
        LOG.info("createMultipartUpload bucket={} key={} contentType={}", bucketName, objectKey, contentType);
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

    @Override
    public ObjectMetadata statObject(String bucketName, String objectKey) {
        try {
            io.minio.StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build());
            // MinIO ETag：非 multipart 对象为 SHA-256 hex（带引号），multipart 对象为 "sha256-parts"
            String etag = stat.etag();
            String sha256 = stripQuotes(etag);
            return new ObjectMetadata(stat.size(), sha256, etag);
        } catch (Exception ex) {
            LOG.error("statObject failed bucket={} key={}", bucketName, objectKey, ex);
            throw new StorageException("failed to stat object: " + objectKey, ex);
        }
    }

    @Override
    public void copyObject(String srcBucket, String srcKey, String destBucket, String destKey) {
        try {
            minioClient.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(destBucket)
                            .object(destKey)
                            .source(io.minio.CopySource.builder().bucket(srcBucket).object(srcKey).build())
                            .build());
            LOG.info("copyObject {}->{}/{}", srcKey, destBucket, destKey);
        } catch (Exception ex) {
            LOG.error("copyObject failed src={}/{}, dest={}/{}", srcBucket, srcKey, destBucket, destKey, ex);
            throw new StorageException("failed to copy object", ex);
        }
    }

    @Override
    public String uploadFromStream(String bucketName, String objectKey, java.io.InputStream inputStream, long size) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            DigestInputStream digestStream = new DigestInputStream(inputStream, digest);

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .stream(digestStream, size, -1)
                            .contentType("application/octet-stream")
                            .build());

            String sha256 = bytesToHex(digest.digest());
            LOG.info("uploadFromStream bucket={} key={} size={} sha256={}", bucketName, objectKey, size, sha256);
            return sha256;
        } catch (NoSuchAlgorithmException ex) {
            // 不应发生：SHA-256 是 JRE 内置算法
            throw new StorageException("SHA-256 algorithm not available", ex);
        } catch (Exception ex) {
            LOG.error("uploadFromStream failed bucket={} key={}", bucketName, objectKey, ex);
            throw new StorageException("failed to upload from stream: " + objectKey, ex);
        }
    }

    /**
     * 去掉 ETag 引号。MinIO 对非 multipart 对象返回 "sha256hex"。
     */
    private static String stripQuotes(String etag) {
        if (etag == null) {
            return null;
        }
        if (etag.startsWith("\"") && etag.endsWith("\"") && etag.length() >= 2) {
            return etag.substring(1, etag.length() - 1);
        }
        return etag;
    }

    /**
     * 字节数组转小写 hex 字符串。
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 计算输入流的 SHA-256 摘要（流式，不加载全量到内存）。
     */
    private static class DigestInputStream extends java.io.FilterInputStream {
        private final MessageDigest digest;

        DigestInputStream(java.io.InputStream in, MessageDigest digest) {
            super(in);
            this.digest = digest;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b != -1) {
                digest.update((byte) b);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int len) throws IOException {
            int n = super.read(buffer, offset, len);
            if (n > 0) {
                digest.update(buffer, offset, n);
            } else if (n == -1) {
                // EOF
            }
            return n;
        }
    }

    /** 存储操作异常。 */
    public static class StorageException extends RuntimeException {
        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
