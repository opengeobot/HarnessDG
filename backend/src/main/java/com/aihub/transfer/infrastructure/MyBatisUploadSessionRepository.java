package com.aihub.transfer.infrastructure;

import com.aihub.shared.api.CursorPage;
import com.aihub.transfer.domain.UploadSession;
import com.aihub.transfer.domain.UploadSessionRepository;
import com.aihub.transfer.domain.UploadSessionStatus;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 上传会话仓储适配器。
 */
@Repository
public class MyBatisUploadSessionRepository implements UploadSessionRepository {

    private final UploadSessionMapper mapper;

    public MyBatisUploadSessionRepository(UploadSessionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(UploadSession session) {
        UploadSessionEntity entity = toEntity(session);
        mapper.insert(entity);
    }

    @Override
    public Optional<UploadSession> findBySessionId(String sessionId) {
        UploadSessionEntity entity = mapper.selectOne(
                Wrappers.<UploadSessionEntity>lambdaQuery()
                        .eq(UploadSessionEntity::getSessionId, sessionId));
        return entity == null ? Optional.empty() : Optional.of(toDomain(entity));
    }

    @Override
    public void update(UploadSession session) {
        mapper.update(null, Wrappers.<UploadSessionEntity>lambdaUpdate()
                .eq(UploadSessionEntity::getSessionId, session.sessionId())
                .set(UploadSessionEntity::getStatus, session.status().name())
                .set(UploadSessionEntity::getMinioUploadId, session.minioUploadId())
                .set(UploadSessionEntity::getUpdatedAt, Instant.now()));
    }

    @Override
    public CursorPage<UploadSession> listByAsset(String assetId, String cursor, int limit) {
        var wrapper = Wrappers.<UploadSessionEntity>lambdaQuery()
                .eq(UploadSessionEntity::getAssetId, assetId)
                .orderByDesc(UploadSessionEntity::getCreatedAt)
                .last("LIMIT " + (limit + 1));
        if (cursor != null && !cursor.isBlank()) {
            wrapper.lt(UploadSessionEntity::getCreatedAt, Instant.parse(cursor));
        }
        List<UploadSessionEntity> entities = mapper.selectList(wrapper);
        boolean hasMore = entities.size() > limit;
        List<UploadSession> items = entities.stream().limit(limit).map(this::toDomain).toList();
        String nextCursor = hasMore && !items.isEmpty()
                ? items.get(items.size() - 1).createdAt().toString() : null;
        return new CursorPage<>(items, nextCursor, hasMore);
    }

    private UploadSessionEntity toEntity(UploadSession s) {
        UploadSessionEntity entity = new UploadSessionEntity();
        entity.setSessionId(s.sessionId());
        entity.setAssetId(s.assetId());
        entity.setVersionId(s.versionId());
        entity.setPrincipalId(s.principalId());
        entity.setStatus(s.status().name());
        entity.setTotalBytes(s.totalBytes());
        entity.setFileCount(s.fileCount());
        entity.setExpiresAt(s.expiresAt());
        entity.setMinioUploadId(s.minioUploadId());
        entity.setCreatedAt(s.createdAt());
        entity.setUpdatedAt(s.updatedAt());
        return entity;
    }

    private UploadSession toDomain(UploadSessionEntity entity) {
        return new UploadSession(
                entity.getSessionId(), entity.getAssetId(), entity.getVersionId(),
                entity.getPrincipalId(), UploadSessionStatus.valueOf(entity.getStatus()),
                entity.getTotalBytes() != null ? entity.getTotalBytes() : 0L,
                entity.getFileCount() != null ? entity.getFileCount() : 0,
                entity.getExpiresAt(), entity.getMinioUploadId(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
