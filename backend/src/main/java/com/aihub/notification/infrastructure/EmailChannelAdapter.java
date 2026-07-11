/*
 * 功能: 邮件通知渠道适配器（SMTP 桩实现，按配置启用）。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.notification.infrastructure;

import com.aihub.notification.domain.Notification;
import com.aihub.notification.domain.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 邮件通知渠道适配器。
 *
 * <p>当前为最小 SMTP 桩：记录结构化日志，不实际外发。
 * 生产启用需配置 {@code aihub.notification.email.enabled=true} 并注入 SMTP 参数。
 */
@Component
@ConditionalOnProperty(name = "aihub.notification.email.enabled", havingValue = "true")
@ConfigurationProperties(prefix = "aihub.notification.email")
public class EmailChannelAdapter implements NotificationChannel {

    private static final Logger LOG = LoggerFactory.getLogger(EmailChannelAdapter.class);

    private String from = "noreply@aihub.local";
    private String smtpHost = "localhost";
    private int smtpPort = 587;

    @Override
    public String channelId() {
        return "email";
    }

    @Override
    public void send(Notification notification, String recipient) {
        LOG.info("email channel stub send notificationId={} eventType={} recipient={} smtpHost={}:{} from={}",
                notification.notificationId(), notification.eventType(), recipient, smtpHost, smtpPort, from);
    }

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }
    public String getSmtpHost() { return smtpHost; }
    public void setSmtpHost(String smtpHost) { this.smtpHost = smtpHost; }
    public int getSmtpPort() { return smtpPort; }
    public void setSmtpPort(int smtpPort) { this.smtpPort = smtpPort; }
}
