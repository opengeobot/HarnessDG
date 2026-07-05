package com.aihub.version.application;

import com.aihub.version.domain.Version;
import com.aihub.version.domain.VersionStatus;
import java.time.Instant;

/** 版本视图。 */
public record VersionView(String versionId, String assetId, String version,
                          VersionStatus status, String sourceCommit,
                          String manifestDigest, String gitTag,
                          Instant publishedAt, String publishedBy,
                          String notes, Instant createdAt) {

    public static VersionView from(Version v) {
        return new VersionView(v.versionId(), v.assetId(), v.version(),
                v.status(), v.sourceCommit(), v.manifestDigest(), v.gitTag(),
                v.publishedAt(), v.publishedBy(), v.notes(), v.createdAt());
    }
}
