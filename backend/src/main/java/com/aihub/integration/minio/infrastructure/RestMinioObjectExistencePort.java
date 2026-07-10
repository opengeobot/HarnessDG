/*
 * 功能: 基于 MinIO SDK 的对象存在性查询适配器。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.integration.minio.infrastructure;

import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * MinIO 对象存在性查询实现。
 */
@Component
public class RestMinioObjectExistencePort implements MinioObjectExistencePort {

    private static final Logger LOG = LoggerFactory.getLogger(RestMinioObjectExistencePort.class);

    private final MinioClient minioClient;

    public RestMinioObjectExistencePort(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @Override
    public boolean objectExists(String bucket, String objectKey) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
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
