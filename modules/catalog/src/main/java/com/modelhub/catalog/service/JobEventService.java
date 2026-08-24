package com.modelhub.catalog.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modelhub.catalog.domain.JobEventEntity;
import com.modelhub.catalog.repo.JobEventRepository;
import com.modelhub.catalog.repo.JobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * job_events 追加写（05 §1）：sequence = job 内 max+1 单调递增。
 * 「查 max 再 +1」在并发写下会读到相同 max 而撞 UNIQUE(job_id, sequence)——
 * 请求线程（newJob 的 created 事件）与 worker 线程（状态迁移事件）可能交错，
 * 因此先对 job 行加悲观锁将同一 job 的事件写入串行化（05 §10.2），
 * @Transactional REQUIRED 使事件与调用方的状态变更同事务提交。
 */
@Service
public class JobEventService {

    private final JobEventRepository events;
    private final JobRepository jobs;
    private final ObjectMapper objectMapper;

    public JobEventService(JobEventRepository events, JobRepository jobs, ObjectMapper objectMapper) {
        this.events = events;
        this.jobs = jobs;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public JobEventEntity record(Long jobId, String eventType, Map<String, Object> data) {
        // job 行锁：并发事务在此排队，max+1 分配不再交错（锁顺序与 updateJob 先改 job 后记事件一致，无死锁）
        jobs.findByIdForUpdate(jobId);
        JobEventEntity event = new JobEventEntity();
        event.setJobId(jobId);
        event.setEventType(eventType);
        event.setSequence(events.findFirstByJobIdOrderBySequenceDesc(jobId)
                .map(e -> e.getSequence() + 1)
                .orElse(1L));
        event.setData(toJson(data));
        event.setCreatedAt(OffsetDateTime.now());
        return events.save(event);
    }

    private String toJson(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("job event data 序列化失败", e);
        }
    }
}
