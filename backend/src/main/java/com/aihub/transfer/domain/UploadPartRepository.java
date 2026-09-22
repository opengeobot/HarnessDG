/*
 * 功能: 上传分片仓储端口。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.transfer.domain;

import java.util.List;

/** 上传分片仓储端口。 */
public interface UploadPartRepository {

    void upsert(UploadPart part);

    void markUploaded(String fileId, int partNumber, String etag, long size);

    List<UploadPart> listByFileId(String fileId);

    int countPendingBySessionId(String sessionId);
}
