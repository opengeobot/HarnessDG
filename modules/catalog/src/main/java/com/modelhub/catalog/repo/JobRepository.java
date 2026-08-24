package com.modelhub.catalog.repo;

import com.modelhub.catalog.domain.JobEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<JobEntity, Long> {

    Optional<JobEntity> findByPublicId(UUID publicId);

    /** 行锁：JobEventService 以 job 行锁串行化同 job 的 sequence 分配（uq_job_event_seq 兜底前的主动互斥）。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from JobEntity j where j.id = :id")
    Optional<JobEntity> findByIdForUpdate(@Param("id") Long id);

    /** Worker 回写异步 Job 状态：取最近一个处于执行中的同聚合 Job。 */

    /** Worker. */
    Optional<JobEntity> findFirstByAggregateTypeAndAggregateIdAndStatusInOrderByCreatedAtDesc(
            String aggregateType, String aggregateId, Collection<String> statuses);

    /** 预览触发幂等：同仓库指定类型的活跃 Job（payload 内比对 commitSha）。 */
    List<JobEntity> findByJobTypeAndAggregateIdAndStatusInOrderByCreatedAtDesc(
            String jobType, String aggregateId, Collection<String> statuses);

    /** 预览 Worker 轮询领取：按类型 + 状态取待处理 Job（创建时间升序）。 */
    List<JobEntity> findByJobTypeAndStatusOrderByCreatedAtAsc(String jobType, String status);
}
