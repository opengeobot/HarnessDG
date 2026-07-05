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

    // ---- 工件操作 ----

    void insertArtifact(Artifact artifact);

    List<Artifact> listArtifactsByVersion(String versionId);

    void deleteArtifactsByVersion(String versionId);
}
