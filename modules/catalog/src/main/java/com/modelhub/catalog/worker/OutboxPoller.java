package com.modelhub.catalog.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modelhub.catalog.config.CatalogProperties;
import com.modelhub.catalog.domain.OutboxEventEntity;
import com.modelhub.catalog.domain.RepositoryEntity;
import com.modelhub.catalog.repo.OutboxEventRepository;
import com.modelhub.catalog.repo.RepositoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Outbox 投递器（05 §10.1 至少一次投递）：
 * - 轮询未投递事件，按事件类型分发到 {@link ProvisioningWorker}；
 * - 成功后标记 published_at；失败按指数退避重试；
 * - provisioning 失败受仓库侧重试上限约束，超限置 failed 等待管理员（05 §8）。
 * 消费端幂等由处理器保证，重复投递不产生副作用。
 */
@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);
    private static final int BATCH_SIZE = 20;
    /** 非 provisioning 事件的最大重试次数（超限进入死信，仅记录）。 */
    private static final int MAX_EVENT_ATTEMPTS = 10;
    private static final long MAX_BACKOFF_SECONDS = 300;

    private final OutboxEventRepository outbox;
    private final RepositoryRepository repositories;
    private final ProvisioningWorker worker;
    private final TransactionTemplate tx;
    private final ObjectMapper objectMapper;
    private final CatalogProperties props;

    public OutboxPoller(OutboxEventRepository outbox, RepositoryRepository repositories,
                        ProvisioningWorker worker, TransactionTemplate tx,
                        ObjectMapper objectMapper, CatalogProperties props) {
        this.outbox = outbox;
        this.repositories = repositories;
        this.worker = worker;
        this.tx = tx;
        this.objectMapper = objectMapper;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${modelhub.catalog.poll-interval-ms:2000}")
    public void poll() {
        List<OutboxEventEntity> batch = tx.execute(s ->
                outbox.findPending(OffsetDateTime.now(), PageRequest.of(0, BATCH_SIZE)));
        if (batch == null || batch.isEmpty()) {
            return;
        }
        for (OutboxEventEntity e : batch) {
            processOne(e.getId(), e.getEventType(), e.getPayload());
        }
    }

    private void processOne(Long eventId, String eventType, String payloadJson) {
        try {
            JsonNode payload = objectMapper.readTree(payloadJson);
            switch (eventType) {
                case "RepositoryProvisionRequested" -> worker.handleProvision(payload);
                case "RepositoryDeletionRequested" -> worker.handleDeletion(payload);
                case "RepositoryRestoreRequested" -> worker.handleRestore(payload);
                default -> log.warn("未知 outbox 事件类型: {}", eventType);
            }
            markPublished(eventId);
        } catch (Exception ex) {
            log.warn("outbox 事件处理失败 id={} type={}: {}", eventId, eventType, ex.getMessage());
            handleFailure(eventId, eventType, payloadJson, ex);
        }
    }

    private void markPublished(Long eventId) {
        tx.executeWithoutResult(s -> outbox.findById(eventId).ifPresent(e -> {
            if (e.getPublishedAt() == null) {
                e.setPublishedAt(OffsetDateTime.now());
                outbox.save(e);
            }
        }));
    }

    private void handleFailure(Long eventId, String eventType, String payloadJson, Exception cause) {
        long repositoryId = repositoryIdOf(payloadJson);
        String error = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        if (error.length() > 500) {
            error = error.substring(0, 500);
        }
        String finalError = error;
        tx.executeWithoutResult(s -> {
            OutboxEventEntity e = outbox.findById(eventId).orElse(null);
            if (e == null || e.getPublishedAt() != null) {
                return;
            }
            e.setAttempts(e.getAttempts() + 1);
            e.setLastError(finalError);
            long backoff = Math.min((1L << Math.min(e.getAttempts(), 8)) * 2, MAX_BACKOFF_SECONDS);
            OffsetDateTime next = OffsetDateTime.now().plusSeconds(backoff);

            if ("RepositoryProvisionRequested".equals(eventType)) {
                // 仓库侧重试上限为真相源（05 §8）：超限置 failed，事件终止投递
                RepositoryEntity repo = repositories.findById(repositoryId).orElse(null);
                if (repo != null && "provisioning".equals(repo.getLifecycleStatus())) {
                    repo.setProvisionRetryCount(repo.getProvisionRetryCount() + 1);
                    repo.setProvisionLastError(finalError);
                    repo.setUpdatedAt(OffsetDateTime.now());
                    if (repo.getProvisionRetryCount() >= props.getProvisionMaxRetries()) {
                        repo.setLifecycleStatus("failed");
                        repo.setProvisionNextRetryAt(null);
                        e.setPublishedAt(OffsetDateTime.now());
                        log.error("provisioning 重试超限，仓库置为 failed: repositoryId={}", repositoryId);
                    } else {
                        repo.setProvisionNextRetryAt(next);
                        e.setNextAttemptAt(next);
                    }
                    repositories.save(repo);
                } else {
                    e.setPublishedAt(OffsetDateTime.now());
                }
            } else if (e.getAttempts() >= MAX_EVENT_ATTEMPTS) {
                // 死信：记录后停止投递，等待人工介入
                e.setPublishedAt(OffsetDateTime.now());
                log.error("outbox 事件重试超限进入死信: id={} type={}", eventId, eventType);
            } else {
                e.setNextAttemptAt(next);
            }
            outbox.save(e);
        });
    }

    private long repositoryIdOf(String payloadJson) {
        try {
            return objectMapper.readTree(payloadJson).path("repositoryId").asLong(0);
        } catch (Exception e) {
            return 0;
        }
    }
}
