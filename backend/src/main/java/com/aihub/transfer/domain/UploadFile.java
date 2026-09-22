/*
 * 功能: 上传文件领域记录。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.transfer.domain;

/** 上传文件记录（一个 session 内多个文件）。 */
public record UploadFile(String fileId, String sessionId, String path, long size,
                         String sha256, String mediaType, int partCount,
                         UploadFileStatus status) {

    public enum UploadFileStatus {
        PENDING, UPLOADING, COMPLETED, FAILED
    }
}
