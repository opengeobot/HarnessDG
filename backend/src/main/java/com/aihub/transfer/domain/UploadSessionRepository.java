package com.aihub.transfer.domain;

import com.aihub.shared.api.CursorPage;
import java.util.Optional;

/**
 * 上传会话仓储端口。
 */
public interface UploadSessionRepository {

    void insert(UploadSession session);

    Optional<UploadSession> findBySessionId(String sessionId);

    void update(UploadSession session);

    CursorPage<UploadSession> listByAsset(String assetId, String cursor, int limit);
}
