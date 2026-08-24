package com.modelhub.artifact.repo;

import com.modelhub.artifact.domain.UploadSessionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UploadSessionRepository extends JpaRepository<UploadSessionEntity, Long> {

    Optional<UploadSessionEntity> findByPublicId(UUID publicId);

    /** 仓库内指定状态集合的会话数，供并发上传配额检查（05 §11，非终态集合）。 */
    long countByRepositoryIdAndStatusIn(Long repositoryId, Collection<String> statuses);

    /** 幂等重放（Idempotency-Key）：同一 repository 同键直接返回既有会话。 */
    Optional<UploadSessionEntity> findByRepositoryIdAndIdempotencyKey(Long repositoryId, String idempotencyKey);

    /** 状态机推进需串行（05 §6 CAS）：悲观行锁防止 complete/abort 并发竞态。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from UploadSessionEntity s where s.id = :id")
    Optional<UploadSessionEntity> findByIdForUpdate(@Param("id") Long id);

    List<UploadSessionEntity> findByStatusIn(List<String> statuses);

    /** Janitor（05 §5/§6.1）：已过期且仍处非终态的会话，供主动过期收敛。 */
    List<UploadSessionEntity> findByExpiresAtBeforeAndStatusIn(OffsetDateTime threshold, Collection<String> statuses);
}
