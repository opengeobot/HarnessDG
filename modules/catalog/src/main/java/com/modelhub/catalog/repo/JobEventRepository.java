package com.modelhub.catalog.repo;

import com.modelhub.catalog.domain.JobEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobEventRepository extends JpaRepository<JobEventEntity, Long> {

    /** SSE 增量拉取：Last-Event-ID 之后的事件按序返回。 */
    List<JobEventEntity> findByJobIdAndSequenceGreaterThanOrderBySequenceAsc(Long jobId, Long sequence);

    /** SSE 全量回放（job 内 sequence 单调递增）。 */
    List<JobEventEntity> findByJobIdOrderBySequenceAsc(Long jobId);

    /** 取 job 当前最大 sequence，用于分配下一个单调序号。 */
    Optional<JobEventEntity> findFirstByJobIdOrderBySequenceDesc(Long jobId);
}
