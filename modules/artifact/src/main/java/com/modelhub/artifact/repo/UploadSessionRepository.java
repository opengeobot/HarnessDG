package com.modelhub.artifact.repo;

import com.modelhub.artifact.domain.UploadSessionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UploadSessionRepository extends JpaRepository<UploadSessionEntity, Long> {

    Optional<UploadSessionEntity> findByPublicId(UUID publicId);

    /** 幂等重放（Idempotency-Key）：同一 repository 同键直接返回既有会话。 */
    Optional<UploadSessionEntity> findByRepositoryIdAndIdempotencyKey(Long repositoryId, String idempotencyKey);

    /** 状态机推进需串行（05 §6 CAS）：悲观行锁防止 complete/abort 并发竞态。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from UploadSessionEntity s where s.id = :id")
    Optional<UploadSessionEntity> findByIdForUpdate(@Param("id") Long id);

    List<UploadSessionEntity> findByStatusIn(List<String> statuses);
}
