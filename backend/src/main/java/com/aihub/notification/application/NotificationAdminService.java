/*
 * 功能: 通知管理应用服务——聚合 Outbox/Webhook 投递摘要与手动重试。
 * 时间: 2026-07-06
 * 作者: AxeXie
 */
package com.aihub.notification.application;

import com.aihub.notification.domain.OutboxEvent;
import com.aihub.notification.domain.OutboxRepository;
import com.aihub.notification.domain.WebhookDelivery;
import com.aihub.notification.domain.WebhookDeliveryRepository;
import com.aihub.notification.domain.WebhookDeliveryStatus;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 通知管理应用服务。
 *
 * <p>提供管理员视角的 Outbox 事件查询、Webhook 投递查询与手动重试能力。
 */
@Service
public class NotificationAdminService {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationAdminService.class);
    private static final int DEFAULT_LIMIT = 50;

    private final OutboxRepository outboxRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final Clock clock;

    public NotificationAdminService(OutboxRepository outboxRepository,
                                     WebhookDeliveryRepository deliveryRepository,
                                     Clock clock) {
        this.outboxRepository = outboxRepository;
        this.deliveryRepository = deliveryRepository;
        this.clock = clock;
    }

    /** 查询最近 Outbox 事件。 */
    public List<OutboxEventView> listOutboxEvents(int limit) {
        int effective = Math.min(Math.max(limit, 1), 200);
        return outboxRepository.listRecent(effective).stream()
                .map(OutboxEventView::from)
                .toList();
    }

    /** 统计 Outbox 待处理事件数。 */
    public long countPendingOutbox() {
        return outboxRepository.countPending();
    }

    /** 查询最近 Webhook 投递记录。 */
    public List<WebhookDeliveryView> listDeliveries(int limit) {
        int effective = Math.min(Math.max(limit, 1), 200);
        return deliveryRepository.listRecent(effective).stream()
                .map(WebhookDeliveryView::from)
                .toList();
    }

    /** 按状态统计投递数。 */
    public long countDeliveriesByStatus(String status) {
        WebhookDeliveryStatus s = WebhookDeliveryStatus.valueOf(status);
        return deliveryRepository.countByStatus(s);
    }

    /** 手动重置失败投递为 PENDING（管理员重试）。 */
    public void retryDelivery(String deliveryId) {
        Instant now = clock.instant();
        boolean reset = deliveryRepository.resetForRetry(deliveryId, now);
        if (!reset) {
            throw new NotFoundException(ErrorCode.NOTIFICATION_NOT_FOUND,
                    "delivery not found or not in retryable state",
                    Map.of("deliveryId", deliveryId));
        }
        LOG.info("admin retry delivery deliveryId={}", deliveryId);
    }

    /** Outbox 事件视图（管理员）。 */
    public record OutboxEventView(String eventId, String aggregateType, String aggregateId,
                                   String eventType, Instant occurredAt, boolean processed,
                                   String traceId) {
        static OutboxEventView from(OutboxEvent e) {
            return new OutboxEventView(e.eventId(), e.aggregateType(), e.aggregateId(),
                    e.eventType(), e.occurredAt(), e.processedAt() != null, e.traceId());
        }
    }

    /** Webhook 投递视图（管理员）。 */
    public record WebhookDeliveryView(String deliveryId, String eventId, String targetUrl,
                                       String status, int attempts,
                                       Integer lastResponseCode, String lastError,
                                       Instant createdAt, Instant updatedAt) {
        static WebhookDeliveryView from(WebhookDelivery d) {
            return new WebhookDeliveryView(d.deliveryId(), d.eventId(), d.targetUrl(),
                    d.status().name(), d.attempts(),
                    d.lastResponseCode(), d.lastError(),
                    d.createdAt(), d.updatedAt());
        }
    }
}
