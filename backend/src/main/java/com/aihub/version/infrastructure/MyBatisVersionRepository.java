package com.aihub.version.infrastructure;

import com.aihub.shared.api.CursorPage;
import com.aihub.version.domain.Artifact;
import com.aihub.version.domain.Version;
import com.aihub.version.domain.VersionRepository;
import com.aihub.version.domain.VersionStatus;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 版本仓储适配器。
 */
@Repository
public class MyBatisVersionRepository implements VersionRepository {

    private final VersionMapper versionMapper;
    private final ArtifactMapper artifactMapper;

    public MyBatisVersionRepository(VersionMapper versionMapper,
                                    ArtifactMapper artifactMapper) {
        this.versionMapper = versionMapper;
        this.artifactMapper = artifactMapper;
    }

    @Override
    public void insert(Version version) {
        VersionEntity entity = toEntity(version);
        versionMapper.insert(entity);
    }

    @Override
    public Optional<Version> findByVersionId(String versionId) {
        VersionEntity entity = versionMapper.selectOne(
                Wrappers.<VersionEntity>lambdaQuery()
                        .eq(VersionEntity::getVersionId, versionId));
        return entity == null ? Optional.empty() : Optional.of(toDomain(entity));
    }

    @Override
    public void update(Version version) {
        versionMapper.update(null, Wrappers.<VersionEntity>lambdaUpdate()
                .eq(VersionEntity::getVersionId, version.versionId())
                .set(VersionEntity::getStatus, version.status().name())
                .set(VersionEntity::getSourceCommit, version.sourceCommit())
                .set(VersionEntity::getManifestDigest, version.manifestDigest())
                .set(VersionEntity::getGitTag, version.gitTag())
                .set(VersionEntity::getPublishedAt, version.publishedAt())
                .set(VersionEntity::getPublishedBy, version.publishedBy())
                .set(VersionEntity::getNotes, version.notes())
                .set(VersionEntity::getRowVersion, version.rowVersion() + 1)
                .set(VersionEntity::getUpdatedAt, Instant.now()));
    }

    @Override
    public CursorPage<Version> listByAsset(String assetId, String cursor, int limit) {
        var wrapper = Wrappers.<VersionEntity>lambdaQuery()
                .eq(VersionEntity::getAssetId, assetId)
                .orderByDesc(VersionEntity::getCreatedAt)
                .last("LIMIT " + (limit + 1));
        if (cursor != null && !cursor.isBlank()) {
            wrapper.lt(VersionEntity::getCreatedAt, Instant.parse(cursor));
        }
        List<VersionEntity> entities = versionMapper.selectList(wrapper);
        boolean hasMore = entities.size() > limit;
        List<Version> items = entities.stream().limit(limit).map(this::toDomain).toList();
        String nextCursor = hasMore && !items.isEmpty()
                ? items.get(items.size() - 1).createdAt().toString() : null;
        return new CursorPage<>(items, nextCursor, hasMore);
    }

    @Override
    public Optional<Version> findByCoordinate(String assetId, String version) {
        VersionEntity entity = versionMapper.selectOne(
                Wrappers.<VersionEntity>lambdaQuery()
                        .eq(VersionEntity::getAssetId, assetId)
                        .eq(VersionEntity::getVersion, version));
        return entity == null ? Optional.empty() : Optional.of(toDomain(entity));
    }

    @Override
    public long countByAssetIdAndStatus(String assetId, VersionStatus status) {
        Long count = versionMapper.selectCount(
                Wrappers.<VersionEntity>lambdaQuery()
                        .eq(VersionEntity::getAssetId, assetId)
                        .eq(VersionEntity::getStatus, status.name()));
        return count == null ? 0L : count;
    }

    @Override
    public Optional<Version> findLatestPublishedByAssetId(String assetId) {
        VersionEntity entity = versionMapper.selectOne(
                Wrappers.<VersionEntity>lambdaQuery()
                        .eq(VersionEntity::getAssetId, assetId)
                        .eq(VersionEntity::getStatus, VersionStatus.PUBLISHED.name())
                        .orderByDesc(VersionEntity::getPublishedAt)
                        .last("LIMIT 1"));
        return entity == null ? Optional.empty() : Optional.of(toDomain(entity));
    }

    @Override
    public void insertArtifact(Artifact artifact) {
        ArtifactEntity entity = new ArtifactEntity();
        entity.setArtifactId(artifact.artifactId());
        entity.setVersionId(artifact.versionId());
        entity.setPath(artifact.path());
        entity.setDvcFile(artifact.dvcFile());
        entity.setDvcHash(artifact.dvcHash());
        entity.setSha256(artifact.sha256());
        entity.setSize(artifact.size());
        entity.setMediaType(artifact.mediaType());
        artifactMapper.insert(entity);
    }

    @Override
    public List<Artifact> listArtifactsByVersion(String versionId) {
        return artifactMapper.selectList(
                Wrappers.<ArtifactEntity>lambdaQuery()
                        .eq(ArtifactEntity::getVersionId, versionId))
                .stream().map(this::toArtifact).toList();
    }

    @Override
    public void deleteArtifactsByVersion(String versionId) {
        artifactMapper.delete(
                Wrappers.<ArtifactEntity>lambdaQuery()
                        .eq(ArtifactEntity::getVersionId, versionId));
    }

    private VersionEntity toEntity(Version v) {
        VersionEntity entity = new VersionEntity();
        entity.setVersionId(v.versionId());
        entity.setAssetId(v.assetId());
        entity.setVersion(v.version());
        entity.setStatus(v.status().name());
        entity.setSourceCommit(v.sourceCommit());
        entity.setManifestDigest(v.manifestDigest());
        entity.setGitTag(v.gitTag());
        entity.setPublishedAt(v.publishedAt());
        entity.setPublishedBy(v.publishedBy());
        entity.setNotes(v.notes());
        entity.setRowVersion(v.rowVersion());
        entity.setCreatedBy(v.createdBy());
        entity.setCreatedAt(v.createdAt());
        entity.setUpdatedAt(v.updatedAt());
        return entity;
    }

    private Version toDomain(VersionEntity entity) {
        return new Version(
                entity.getVersionId(), entity.getAssetId(), entity.getVersion(),
                VersionStatus.valueOf(entity.getStatus()),
                entity.getSourceCommit(), entity.getManifestDigest(),
                entity.getGitTag(), entity.getPublishedAt(), entity.getPublishedBy(),
                entity.getNotes(),
                entity.getRowVersion() != null ? entity.getRowVersion() : 1L,
                entity.getCreatedBy(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private Artifact toArtifact(ArtifactEntity entity) {
        return new Artifact(
                entity.getArtifactId(), entity.getVersionId(), entity.getPath(),
                entity.getDvcFile(), entity.getDvcHash(), entity.getSha256(),
                entity.getSize() != null ? entity.getSize() : 0L,
                entity.getMediaType());
    }
}
