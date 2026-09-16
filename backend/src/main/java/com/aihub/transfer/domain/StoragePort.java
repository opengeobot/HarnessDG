package com.aihub.transfer.domain;

import java.net.URL;
import java.time.Duration;
import java.util.List;

/**
 * 对象存储端口。
 *
 * <p>业务模块（transfer/version）通过此端口与对象存储交互，不直接依赖 MinIO SDK。
 * 由 integration 模块实现。
 */
public interface StoragePort {

    /** 检查 Bucket 是否存在。 */
    boolean bucketExists(String bucketName);

    /** 创建 Bucket（若不存在）。 */
    void createBucket(String bucketName);

    /** 创建 Multipart Upload 并返回 uploadId。 */
    String createMultipartUpload(String bucketName, String objectKey, String contentType);

    /** 为 Multipart Upload 的单个 Part 签发预签名 PUT URL。 */
    URL presignPartUpload(String bucketName, String objectKey, String uploadId,
                          int partNumber, Duration expiry);

    /** 完成 Multipart Upload（合并所有已上传的 Part）。 */
    void completeMultipartUpload(String bucketName, String objectKey, String uploadId,
                                 List<PartInfo> parts);

    /** 中止 Multipart Upload。 */
    void abortMultipartUpload(String bucketName, String objectKey, String uploadId);

    /** 签发对象下载预签名 GET URL。 */
    URL presignDownload(String bucketName, String objectKey, Duration expiry);

    /** 签发对象上传预签名 PUT URL（单文件直传）。 */
    URL presignUpload(String bucketName, String objectKey, String contentType, Duration expiry);

    /** 删除对象。 */
    void deleteObject(String bucketName, String objectKey);

    /**
     * 获取对象元数据（DVC 等价 stat）。
     *
     * @return 包含 size 和 sha256 (MinIO ETag for non-multipart) 的对象元数据
     */
    ObjectMetadata statObject(String bucketName, String objectKey);

    /**
     * 在 MinIO 内复制对象（DVC 等价 push）。
     */
    void copyObject(String srcBucket, String srcKey, String destBucket, String destKey);

    /**
     * 从流上传对象并计算 SHA-256（DVC 等价 add）。
     *
     * @param bucketName  目标 Bucket
     * @param objectKey   对象 Key
     * @param inputStream 数据流
     * @param size        数据大小
     * @return 上传内容的 SHA-256 摘要（小写 hex）
     */
    String uploadFromStream(String bucketName, String objectKey, java.io.InputStream inputStream, long size);

    /** Multipart Part 信息。 */
    record PartInfo(int partNumber, String etag) {}

    /**
     * 对象元数据（DVC 等价 stat 结果）。
     *
     * @param size   对象大小（字节）
     * @param sha256 对象 SHA-256 摘要（MinIO ETag for non-multipart objects）
     * @param etag   原始 ETag 值（可能包含引号）
     */
    record ObjectMetadata(long size, String sha256, String etag) {}
}
