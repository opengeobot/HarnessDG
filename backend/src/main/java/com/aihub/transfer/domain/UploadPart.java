/*
 * 功能: 上传分片领域记录。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.transfer.domain;

import java.time.Instant;

/** 上传分片记录。 */
public record UploadPart(String fileId, int partNumber, long size, String etag,
                         String presignedUrl, Instant uploadedAt) {
}
