/*
 * 功能: 上传文件仓储端口。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.transfer.domain;

import java.util.List;
import java.util.Optional;

/** 上传文件仓储端口。 */
public interface UploadFileRepository {

    void insert(UploadFile file);

    void update(UploadFile file);

    Optional<UploadFile> findByFileId(String fileId);

    List<UploadFile> listBySessionId(String sessionId);

    Optional<UploadFile> findSessionBundleFile(String sessionId);
}
