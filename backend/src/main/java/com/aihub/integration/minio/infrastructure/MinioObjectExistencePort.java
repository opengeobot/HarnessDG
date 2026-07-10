/*
 * 功能: MinIO 对象存在性查询端口，供存储对账 Worker 校验 PG 记录与对象存储一致性。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.integration.minio.infrastructure;

/**
 * MinIO 对象存在性查询端口。
 */
public interface MinioObjectExistencePort {

    /**
     * 判断对象是否存在于指定 Bucket。
     *
     * @param bucket    Bucket 名称
     * @param objectKey 对象键
     * @return {@code true} 表示对象存在
     */
    boolean objectExists(String bucket, String objectKey);
}
