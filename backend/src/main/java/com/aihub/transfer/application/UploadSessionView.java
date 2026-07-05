package com.aihub.transfer.application;

import com.aihub.transfer.domain.UploadSession;
import com.aihub.transfer.domain.UploadSessionStatus;
import java.time.Instant;

/** 上传会话视图。 */
public record UploadSessionView(String sessionId, String assetId, String versionId,
                                String principalId, UploadSessionStatus status,
                                long totalBytes, int fileCount,
                                Instant expiresAt, Instant createdAt) {

    public static UploadSessionView from(UploadSession s) {
        return new UploadSessionView(s.sessionId(), s.assetId(), s.versionId(),
                s.principalId(), s.status(), s.totalBytes(), s.fileCount(),
                s.expiresAt(), s.createdAt());
    }
}
