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

    /** Multipart Part 信息。 */
    record PartInfo(int partNumber, String etag) {}
}
