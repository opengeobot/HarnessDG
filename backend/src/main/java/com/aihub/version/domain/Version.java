package com.aihub.version.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * 版本聚合根。
 *
 * <p>封装版本生命周期不变量：状态机校验、Manifest 摘要绑定、发布冻结。
 * 领域层不依赖 Spring / MyBatis / 任何基础设施 SDK。
 */
public final class Version {

    private final String versionId;
    private final String assetId;
    private final String version;
    private VersionStatus status;
    private String sourceCommit;
    private String manifestDigest;
    private String gitTag;
    private Instant publishedAt;
    private String publishedBy;
    private String notes;
    private long rowVersion;
    private final String createdBy;
    private final Instant createdAt;
    private Instant updatedAt;

    public Version(String versionId, String assetId, String version, VersionStatus status,
                   String sourceCommit, String manifestDigest, String gitTag,
                   Instant publishedAt, String publishedBy, String notes,
                   long rowVersion, String createdBy, Instant createdAt, Instant updatedAt) {
        this.versionId = Objects.requireNonNull(versionId);
        this.assetId = Objects.requireNonNull(assetId);
        this.version = Objects.requireNonNull(version);
        this.status = Objects.requireNonNull(status);
        this.sourceCommit = sourceCommit;
        this.manifestDigest = manifestDigest;
        this.gitTag = gitTag;
        this.publishedAt = publishedAt;
        this.publishedBy = publishedBy;
        this.notes = notes;
        this.rowVersion = rowVersion;
        this.createdBy = Objects.requireNonNull(createdBy);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    /** 创建草稿版本。 */
    public static Version createDraft(String versionId, String assetId, String version,
                                      String createdBy) {
        Instant now = Instant.now();
        return new Version(versionId, assetId, version, VersionStatus.DRAFT,
                null, null, null, null, null, null, 1L, createdBy, now, now);
    }

    /** 状态转换。 */
    public void transitionTo(VersionStatus target) {
        this.status.assertTransitionTo(target);
        this.status = target;
        this.updatedAt = Instant.now();
    }

    /** 发布版本（冻结 Manifest 摘要 + Git Tag）。 */
    public void publish(String manifestDigest, String gitTag, String publishedBy) {
        if (this.status != VersionStatus.PENDING_REVIEW) {
            throw new IllegalStateException(
                    "only PENDING_REVIEW versions can be published, current: " + this.status);
        }
        this.manifestDigest = Objects.requireNonNull(manifestDigest);
        this.gitTag = Objects.requireNonNull(gitTag);
        this.publishedBy = Objects.requireNonNull(publishedBy);
        this.publishedAt = Instant.now();
        this.status = VersionStatus.PUBLISHED;
        this.updatedAt = Instant.now();
    }

    /** 绑定 Manifest 摘要（VALIDATING 阶段）。 */
    public void bindManifestDigest(String digest) {
        if (this.status != VersionStatus.VALIDATING && this.status != VersionStatus.DRAFT) {
            throw new IllegalStateException(
                    "manifest digest can only be bound in DRAFT/VALIDATING, current: " + this.status);
        }
        this.manifestDigest = digest;
        this.updatedAt = Instant.now();
    }

    /** 绑定源 Commit。 */
    public void bindSourceCommit(String commit) {
        this.sourceCommit = commit;
        this.updatedAt = Instant.now();
    }

    /** 更新备注。 */
    public void updateNotes(String notes) {
        this.notes = notes;
        this.updatedAt = Instant.now();
    }

    // ---- accessors ----

    public String versionId() { return versionId; }
    public String assetId() { return assetId; }
    public String version() { return version; }
    public VersionStatus status() { return status; }
    public String sourceCommit() { return sourceCommit; }
    public String manifestDigest() { return manifestDigest; }
    public String gitTag() { return gitTag; }
    public Instant publishedAt() { return publishedAt; }
    public String publishedBy() { return publishedBy; }
    public String notes() { return notes; }
    public long rowVersion() { return rowVersion; }
    public String createdBy() { return createdBy; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
