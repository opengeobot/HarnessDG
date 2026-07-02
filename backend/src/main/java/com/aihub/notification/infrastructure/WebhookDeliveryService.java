/*
 * 功能: Webhook 投递服务——领取待投递事件、签名、HTTP POST、指数退避重试、DEAD 放弃。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.notification.infrastructure;

import com.aihub.notification.domain.OutboxEvent;
import com.aihub.notification.domain.OutboxRepository;
import com.aihub.notification.domain.WebhookDelivery;
import com.aihub.notification.domain.WebhookDeliveryRepository;
import com.aihub.notification.domain.WebhookDeliveryStatus;
import com.aihub.platform.observability.application.PlatformMetrics;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Webhook 投递服务。
 *
 * <p>定时拉取 Outbox 待投递事件，对 headers 中声明的 webhook 目标做签名投递。
 * SSRF 防护在投递前执行；签名头 {@code X-AIHub-Signature} / {@code X-AIHub-Timestamp} / {@code X-AIHub-Delivery-Id}。
 * 失败按指数退避重试，超阈值进入 DEAD。签名密钥不入库/日志。
 */
@Component
public class WebhookDeliveryService {

    private static final Logger LOG = LoggerFactory.getLogger(WebhookDeliveryService.class);
    private static final int MAX_ATTEMPTS = 5;

    private final OutboxRepository outboxRepository;
    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final WebhookSigner signer;
    private final SsrfGuard ssrfGuard;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final HttpClient httpClient;
    private final long baseBackoffMillis;
    private final long capBackoffMillis;
    private final ObjectProvider<PlatformMetrics> platformMetricsProvider;

    public WebhookDeliveryService(OutboxRepository outboxRepository,
                                   WebhookDeliveryRepository webhookDeliveryRepository,
                                   WebhookSigner signer, SsrfGuard ssrfGuard,
                                   IdGenerator idGenerator, Clock clock,
                                   @Value("${aihub.webhook.backoff-base-ms:5000}") long baseBackoffMillis,
                                   @Value("${aihub.webhook.backoff-cap-ms:600000}") long capBackoffMillis,
                                   ObjectProvider<PlatformMetrics> platformMetricsProvider) {
        this.outboxRepository = outboxRepository;
        this.webhookDeliveryRepository = webhookDeliveryRepository;
        this.signer = signer;
        this.ssrfGuard = ssrfGuard;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.baseBackoffMillis = baseBackoffMillis;
        this.capBackoffMillis = capBackoffMillis;
        this.platformMetricsProvider = platformMetricsProvider;
    }

    /**
     * 投递一批待处理 Outbox 事件。tick 内部捕获所有异常，绝不向外抛出导致调度中断。
     */
    public void tick() {
        List<OutboxEvent> events = outboxRepository.claimPending(10);
        for (OutboxEvent event : events) {
            deliverEvent(event);
        }
        // 投递已创建的 webhook_delivery 重试记录
        retryFailedDeliveries();
    }

    private void deliverEvent(OutboxEvent event) {
        String targetUrl = extractTargetUrl(event);
        if (targetUrl == null) {
            // 无 webhook 目标，直接标记已处理（如仅站内通知的事件）
            outboxRepository.markProcessed(event.eventId(), clock.instant());
            return;
        }
        try {
            ssrfGuard.validate(targetUrl);
        } catch (Exception ex) {
            LOG.warn("webhook target rejected (SSRF) eventId={} url={}", event.eventId(), targetUrl);
            outboxRepository.markProcessed(event.eventId(), clock.instant());
            return;
        }
        String deliveryId = idGenerator.generate(IdPrefix.WEBHOOK);
        String signatureHeader = signer.sign(deliveryId, event.payload());
        Instant now = clock.instant();
        WebhookDelivery delivery = new WebhookDelivery(
                null, deliveryId, event.eventId(), targetUrl, event.payload(),
                signatureHeader, WebhookDeliveryStatus.PENDING, 0, null, null, null, now, now);
        webhookDeliveryRepository.insert(delivery);
        attemptDelivery(delivery, event);
    }

    private void attemptDelivery(WebhookDelivery delivery, OutboxEvent event) {
        int attempt = delivery.attempts() + 1;
        Instant now = clock.instant();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(delivery.targetUrl()))
                    .header("Content-Type", "application/json")
                    .header("X-AIHub-Signature", delivery.signatureHeader())
                    .header("X-AIHub-Timestamp", String.valueOf(Instant.now().getEpochSecond()))
                    .header("X-AIHub-Delivery-Id", delivery.deliveryId())
                    .POST(HttpRequest.BodyPublishers.ofString(delivery.payload() == null ? "{}" : delivery.payload()))
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode >= 200 && statusCode < 300) {
                webhookDeliveryRepository.markDelivered(delivery.deliveryId(), statusCode, now);
                outboxRepository.markProcessed(event.eventId(), now);
                recordWebhookMetric(WebhookDeliveryStatus.DELIVERED.name());
                LOG.info("webhook delivered eventId={} deliveryId={} status={}",
                        event.eventId(), delivery.deliveryId(), statusCode);
            } else {
                handleDeliveryFailure(delivery, event, attempt, statusCode, "HTTP " + statusCode);
            }
        } catch (IOException | InterruptedException ex) {
            handleDeliveryFailure(delivery, event, attempt, null, ex.getMessage());
        } catch (Exception ex) {
            handleDeliveryFailure(delivery, event, attempt, null, ex.getMessage());
        }
    }

    private void handleDeliveryFailure(WebhookDelivery delivery, OutboxEvent event,
                                        int attempt, Integer httpStatus, String error) {
        Instant now = clock.instant();
        if (attempt >= MAX_ATTEMPTS) {
            webhookDeliveryRepository.markDead(delivery.deliveryId(), attempt, httpStatus, truncate(error), now);
            outboxRepository.markProcessed(event.eventId(), now);
            recordWebhookMetric(WebhookDeliveryStatus.DEAD.name());
            LOG.warn("webhook dead eventId={} deliveryId={} attempts={}",
                    event.eventId(), delivery.deliveryId(), attempt);
        } else {
            Instant nextRetry = now.plusMillis(calculateBackoff(attempt));
            webhookDeliveryRepository.markFailed(delivery.deliveryId(), attempt, httpStatus,
                    truncate(error), nextRetry, now);
            recordWebhookMetric(WebhookDeliveryStatus.FAILED.name());
            LOG.info("webhook retry eventId={} deliveryId={} attempts={} nextRetry={}",
                    event.eventId(), delivery.deliveryId(), attempt, nextRetry);
        }
    }

    private void retryFailedDeliveries() {
        Instant now = clock.instant();
        List<WebhookDelivery> retryable = webhookDeliveryRepository.claimRetryable(now, 10);
        for (WebhookDelivery delivery : retryable) {
            if (delivery.status() == WebhookDeliveryStatus.PENDING) {
                // 已在 deliverEvent 中首次投递过，跳过
                continue;
            }
            // 构造临时 OutboxEvent 用于重试
            OutboxEvent placeholder = new OutboxEvent(null, delivery.eventId(), "", "",
                    "", delivery.payload(), null, now, null, null, null);
            attemptDelivery(delivery, placeholder);
        }
    }

    private long calculateBackoff(int attempt) {
        long exp = Math.min(baseBackoffMillis * (1L << Math.max(0, attempt - 1)), capBackoffMillis);
        long jitter = java.util.concurrent.ThreadLocalRandom.current().nextLong(exp / 2 + 1, exp + 1);
        return jitter;
    }

    private static String extractTargetUrl(OutboxEvent event) {
        if (event.headers() == null || event.headers().isBlank()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(event.headers());
            return node.path("webhookUrl").asText(null);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 1000 ? s.substring(0, 1000) : s;
    }

    private void recordWebhookMetric(String status) {
        if (platformMetricsProvider == null) {
            return;
        }
        PlatformMetrics metrics = platformMetricsProvider.getIfAvailable();
        if (metrics != null) {
            metrics.recordWebhookDelivery(status);
        }
    }
}
