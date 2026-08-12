package com.modelhub.catalog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modelhub.catalog.domain.OutboxEventEntity;
import com.modelhub.catalog.repo.OutboxEventRepository;
import com.modelhub.shared.id.PublicIds;
import com.modelhub.shared.web.TraceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Transactional Outbox 写入侧（05 §10.1）：必须在业务事务内调用，
 * 与数据库状态变更同事务提交，publisher 至少一次投递。
 */
@Service
public class OutboxService {

    private final OutboxEventRepository outbox;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository outbox, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEventEntity publish(String eventType, String aggregateId, Long aggregateVersion,
                                     Object payload) {
        OutboxEventEntity e = new OutboxEventEntity();
        e.setEventId(PublicIds.next());
        e.setEventType(eventType);
        e.setSchemaVersion(1);
        e.setAggregateId(aggregateId);
        e.setAggregateVersion(aggregateVersion);
        try {
            e.setPayload(objectMapper.writeValueAsString(payload));
        } catch (Exception ex) {
            throw new IllegalStateException("outbox payload 序列化失败: " + eventType, ex);
        }
        e.setTraceId(TraceContext.currentTraceId());
        e.setOccurredAt(OffsetDateTime.now());
        e.setCreatedAt(OffsetDateTime.now());
        e.setAttempts(0);
        e.setNextAttemptAt(OffsetDateTime.now());
        return outbox.save(e);
    }
}
