package com.modelhub.catalog.repo;

import com.modelhub.catalog.domain.OutboxEventEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {

    /** 未投递事件按写入顺序取一批（publisher 轮询入口）。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from OutboxEventEntity e where e.publishedAt is null "
            + "and e.nextAttemptAt <= :now order by e.id")
    List<OutboxEventEntity> findPending(@Param("now") OffsetDateTime now, Pageable pageable);

    Optional<OutboxEventEntity> findByEventId(java.util.UUID eventId);
}
