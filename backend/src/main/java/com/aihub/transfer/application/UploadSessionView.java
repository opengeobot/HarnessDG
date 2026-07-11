package com.aihub.transfer.application;

import com.aihub.transfer.domain.UploadSession;
import com.aihub.transfer.domain.UploadSessionStatus;
import java.time.Instant;

/** 上传会话视图。 */
public record UploadSessionView(String sessionId, String assetId, String versionId,
                                String principalId, UploadSessionStatus status,
                                long totalBytes, int fileCount,
                                Instant expiresAt, Instant createdAt,
                                String materializeJobId,
                                java.util.List<UploadPartView> parts) {

    public static UploadSessionView from(UploadSession s) {
        return from(s, null, java.util.List.of());
    }

    public static UploadSessionView from(UploadSession s, String materializeJobId) {
        return from(s, materializeJobId, java.util.List.of());
    }

    public static UploadSessionView from(UploadSession s, String materializeJobId,
                                         java.util.List<UploadPartView> parts) {
        return new UploadSessionView(s.sessionId(), s.assetId(), s.versionId(),
                s.principalId(), s.status(), s.totalBytes(), s.fileCount(),
                s.expiresAt(), s.createdAt(), materializeJobId, parts);
    }
}
