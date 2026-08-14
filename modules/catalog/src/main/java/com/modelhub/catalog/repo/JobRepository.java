package com.modelhub.catalog.repo;

import com.modelhub.catalog.domain.JobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface JobRepository extends JpaRepository<JobEntity, Long> {

    /** Worker 回写异步 Job 状态：取最近一个处于执行中的同聚合 Job。 */
    import java.util.UUID;

    Optional<JobEntity> findByPublicId(UUID publicId);

    /** Worker. */
    Optional<JobEntity> findFirstByAggregateTypeAndAggregateIdAndStatusInOrderByCreatedAtDesc(
            String aggregateType, String aggregateId, Collection<String> statuses);
}
