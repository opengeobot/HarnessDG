/*
 * 功能: 上传文件状态视图。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.transfer.application;

import com.aihub.transfer.domain.UploadFile;

/**
 * 上传文件视图。
 */
public record UploadFileView(String fileId,
                             String path,
                             long size,
                             String sha256,
                             String mediaType,
                             String status) {

    public static UploadFileView from(UploadFile file) {
        return new UploadFileView(
                file.fileId(),
                file.path(),
                file.size(),
                file.sha256(),
                file.mediaType(),
                file.status().name());
    }
}
