/*
 * 功能: Webhook 通知渠道适配器，将站内通知桥接到 Outbox/Webhook 投递管线。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.notification.infrastructure;

import com.aihub.notification.domain.Notification;
import com.aihub.notification.domain.NotificationChannel;
import com.aihub.notification.domain.OutboxEvent;
import com.aihub.notification.domain.OutboxRepository;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Webhook 通知渠道适配器。
 *
 * <p>将通知事件写入 Outbox 并附带 webhook 路由头，由 {@link WebhookDeliveryService} 异步投递。
 */
@Component
public class WebhookChannelAdapter implements NotificationChannel {

    private static final Logger LOG = LoggerFactory.getLogger(WebhookChannelAdapter.class);

    private final OutboxRepository outboxRepository;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public WebhookChannelAdapter(OutboxRepository outboxRepository,
                                 IdGenerator idGenerator,
                                 Clock clock) {
        this.outboxRepository = outboxRepository;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    public String channelId() {
        return "webhook";
    }

    @Override
    public void send(Notification notification, String recipient) {
        Instant now = clock.instant();
        OutboxEvent event = new OutboxEvent(
                null,
                idGenerator.generate(IdPrefix.REQUEST),
                "NOTIFICATION",
                notification.notificationId(),
                notification.eventType(),
                notification.parameters(),
                toJsonHeaders(recipient),
                now,
                null,
                null,
                notification.principalId());
        outboxRepository.append(event);
        LOG.debug("webhook channel enqueued notificationId={} target={}", notification.notificationId(), recipient);
    }

    private static String toJsonHeaders(String webhookUrl) {
        return "{\"webhookUrl\":\"" + webhookUrl.replace("\"", "\\\"") + "\"}";
    }
}
