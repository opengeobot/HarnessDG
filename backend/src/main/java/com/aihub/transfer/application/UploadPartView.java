/*
 * 功能: 上传分片状态视图。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.transfer.application;

import com.aihub.transfer.domain.UploadPart;
import java.time.Instant;

/**
 * 上传分片视图。
 */
public record UploadPartView(int partNumber,
                             long size,
                             String etag,
                             String status,
                             Instant uploadedAt) {

    public static UploadPartView from(UploadPart part) {
        String status = part.etag() != null && !part.etag().isBlank() ? "COMPLETED" : "PENDING";
        return new UploadPartView(part.partNumber(), part.size(), part.etag(), status, part.uploadedAt());
    }
}
