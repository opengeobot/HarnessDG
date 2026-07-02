/*
 * 功能: notification 模块装配——Outbox 投递器与 Webhook 重试调度器。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.notification.infrastructure;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * notification 模块装配。
 *
 * <p>以受管 {@code schedulerExecutor} 驱动 Outbox 投递器定时拉取待投递事件。
 * 调度受 {@code aihub.notification.dispatcher.enabled} 控制（默认开启，测试 Profile 可关闭）。
 */
@Configuration
public class NotificationConfiguration {

    /**
     * Outbox 投递调度器：以受管 schedulerExecutor 定时驱动 {@link WebhookDeliveryService#tick()}。
     */
    @Bean
    @ConditionalOnProperty(name = "aihub.notification.dispatcher.enabled", havingValue = "true", matchIfMissing = true)
    public OutboxDispatcherScheduler outboxDispatcherScheduler(WebhookDeliveryService deliveryService,
                                                                ThreadPoolTaskScheduler schedulerExecutor) {
        return new OutboxDispatcherScheduler(deliveryService, schedulerExecutor);
    }

    /**
     * 投递调度器实现。应用就绪后以固定速率调度 tick；销毁时取消调度。
     */
    public static class OutboxDispatcherScheduler implements ApplicationListener<ApplicationReadyEvent> {

        private final WebhookDeliveryService deliveryService;
        private final ThreadPoolTaskScheduler scheduler;
        private ScheduledFuture<?> scheduledFuture;

        OutboxDispatcherScheduler(WebhookDeliveryService deliveryService, ThreadPoolTaskScheduler scheduler) {
            this.deliveryService = deliveryService;
            this.scheduler = scheduler;
        }

        @Override
        public void onApplicationEvent(ApplicationReadyEvent event) {
            scheduledFuture = scheduler.scheduleAtFixedRate(deliveryService::tick, Duration.ofSeconds(10));
        }

        public void stop() {
            if (scheduledFuture != null) {
                scheduledFuture.cancel(false);
            }
        }
    }
}
