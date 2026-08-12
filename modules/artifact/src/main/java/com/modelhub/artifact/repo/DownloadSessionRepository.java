package com.modelhub.artifact.repo;

import com.modelhub.artifact.domain.DownloadSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DownloadSessionRepository extends JpaRepository<DownloadSessionEntity, Long> {

    Optional<DownloadSessionEntity> findByPublicId(UUID publicId);

    /** 幂等签发（Idempotency-Key）：同 repository 同键复用既有下载会话。 */
    Optional<DownloadSessionEntity> findByRepositoryIdAndIdempotencyKey(Long repositoryId, String idempotencyKey);

    List<DownloadSessionEntity> findByFileVersionId(Long fileVersionId);
}
