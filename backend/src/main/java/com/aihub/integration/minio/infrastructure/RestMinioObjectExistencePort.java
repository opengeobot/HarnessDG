/*
 * 功能: 基于 MinIO SDK 的对象存在性查询适配器（按桶路由凭据：dvc-cache 走 DVC scoped 客户端）。
 * 时间: 2026-09-29
 * 作者: AxeXie
 */
package com.aihub.integration.minio.infrastructure;

import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * MinIO 对象存在性查询实现。
 */
@Component
public class RestMinioObjectExistencePort implements MinioObjectExistencePort {

    private static final Logger LOG = LoggerFactory.getLogger(RestMinioObjectExistencePort.class);

    /** DVC 缓存桶：凭据隔离归 DVC scoped 账号，主账号无该桶权限。 */
    private static final String DVC_CACHE_BUCKET = "dvc-cache";

    private final MinioClient minioClient;
    private final MinioClient dvcCacheMinioClient;

    public RestMinioObjectExistencePort(MinioClient minioClient,
                                        @Qualifier("dvcCacheMinioClient") MinioClient dvcCacheMinioClient) {
        this.minioClient = minioClient;
        this.dvcCacheMinioClient = dvcCacheMinioClient;
    }

    @Override
    public boolean objectExists(String bucket, String objectKey) {
        MinioClient client = DVC_CACHE_BUCKET.equals(bucket) ? dvcCacheMinioClient : minioClient;
        try {
            client.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
            return true;
        } catch (ErrorResponseException ex) {
            if ("NoSuchKey".equals(ex.errorResponse().code())
                    || "NoSuchObject".equals(ex.errorResponse().code())) {
                return false;
            }
            LOG.warn("minio statObject failed bucket={} key={}: {}", bucket, objectKey, ex.getMessage());
            return false;
        } catch (Exception ex) {
            LOG.warn("minio statObject failed bucket={} key={}: {}", bucket, objectKey, ex.getMessage());
            return false;
        }
    }
}
