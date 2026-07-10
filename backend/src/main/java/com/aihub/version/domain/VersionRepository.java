package com.aihub.version.domain;

import com.aihub.shared.api.CursorPage;
import java.util.List;
import java.util.Optional;

/**
 * 版本仓储端口。
 */
public interface VersionRepository {

    void insert(Version version);

    Optional<Version> findByVersionId(String versionId);

    void update(Version version);

    CursorPage<Version> listByAsset(String assetId, String cursor, int limit);

    Optional<Version> findByCoordinate(String assetId, String version);

    /** 统计资产下处于指定状态的版本数量（用于归档前活跃版本守卫）。 */
    long countByAssetIdAndStatus(String assetId, VersionStatus status);

    /** 查询资产最新已发布版本（按 published_at 降序取首条）。 */
    Optional<Version> findLatestPublishedByAssetId(String assetId);

    // ---- 工件操作 ----

    void insertArtifact(Artifact artifact);

    List<Artifact> listArtifactsByVersion(String versionId);

    void deleteArtifactsByVersion(String versionId);
}
