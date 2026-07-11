/*
 * 功能: 即时通讯通知渠道适配器（桩实现，记录日志供后续对接企业 IM）。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.notification.infrastructure;

import com.aihub.notification.domain.Notification;
import com.aihub.notification.domain.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * IM 通知渠道适配器（桩）。
 *
 * <p>预留企业微信/钉钉/Slack 等 IM 对接点；当前仅记录结构化日志。
 * 详见 {@code docs/runbooks/notification-channels.md}。
 */
@Component
public class ImChannelAdapter implements NotificationChannel {

    private static final Logger LOG = LoggerFactory.getLogger(ImChannelAdapter.class);

    @Override
    public String channelId() {
        return "im";
    }

    @Override
    public void send(Notification notification, String recipient) {
        LOG.info("im channel stub send notificationId={} eventType={} recipient={}",
                notification.notificationId(), notification.eventType(), recipient);
    }
}
