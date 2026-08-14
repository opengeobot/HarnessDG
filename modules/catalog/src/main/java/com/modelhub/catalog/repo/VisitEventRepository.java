package com.modelhub.catalog.repo;

import com.modelhub.catalog.domain.VisitEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

public interface VisitEventRepository extends JpaRepository<VisitEventEntity, Long> {

    long countByRepositoryId(Long repositoryId);

    /** 幂等去重插入：唯一约束冲突静默忽略，返回受影响行数（0=窗口内已记录）。 */
    @Modifying
    @Query(value = "INSERT INTO visit_events(repository_id, visitor_hash, window_start, created_at) "
            + "VALUES (:repositoryId, :visitorHash, :windowStart, now()) "
            + "ON CONFLICT (repository_id, visitor_hash, window_start) DO NOTHING",
            nativeQuery = true)
    int insertIgnore(@Param("repositoryId") Long repositoryId,
                     @Param("visitorHash") String visitorHash,
                     @Param("windowStart") OffsetDateTime windowStart);
}
