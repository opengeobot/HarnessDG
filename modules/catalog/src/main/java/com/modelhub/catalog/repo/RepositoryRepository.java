package com.modelhub.catalog.repo;

import com.modelhub.catalog.domain.RepositoryEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositoryRepository extends JpaRepository<RepositoryEntity, Long> {

    Optional<RepositoryEntity> findByPublicId(UUID publicId);

    Optional<RepositoryEntity> findByNamespaceIdAndResourceTypeAndNormalizedName(
            Long namespaceId, String resourceType, String normalizedName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RepositoryEntity r where r.id = :id")
    Optional<RepositoryEntity> findByIdForUpdate(@Param("id") Long id);

    /** Purge 候选（05 §9.2 第 4-5 步）：deleted 且保留期已到，或上轮失败遗留的 purging（幂等重试）。 */
    @Query("select r from RepositoryEntity r where r.lifecycleStatus = 'purging' "
            + "or (r.lifecycleStatus = 'deleted' and r.retentionUntil is not null and r.retentionUntil < :now) "
            + "order by r.id")
    List<RepositoryEntity> findPurgeCandidates(@Param("now") OffsetDateTime now);
}
