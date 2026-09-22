/*
 * 功能: 邮件通知渠道适配器——Jakarta Mail SMTP 外发。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.notification.infrastructure;

import com.aihub.notification.domain.Notification;
import com.aihub.notification.domain.NotificationChannel;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * 邮件通知渠道适配器。
 *
 * <p>通过 Spring Mail / Jakarta Mail 发送 SMTP 邮件。{@code recipient} 可为邮箱地址或
 * {@code principal_id}（经 {@link PrincipalEmailResolver} 查询 {@code iam_user.email}）。
 * 非用户主体或缺失邮箱时跳过投递并记录结构化日志。
 */
@Component
@ConditionalOnProperty(name = "aihub.notification.email.enabled", havingValue = "true")
public class EmailChannelAdapter implements NotificationChannel {

    private static final Logger LOG = LoggerFactory.getLogger(EmailChannelAdapter.class);

    private final JavaMailSender mailSender;
    private final PrincipalEmailResolver emailResolver;
    private final EmailChannelProperties properties;

    public EmailChannelAdapter(JavaMailSender mailSender,
                               PrincipalEmailResolver emailResolver,
                               EmailChannelProperties properties) {
        this.mailSender = mailSender;
        this.emailResolver = emailResolver;
        this.properties = properties;
    }

    @Override
    public String channelId() {
        return "email";
    }

    @Override
    public void send(Notification notification, String recipient) {
        emailResolver.resolveEmail(recipient).ifPresentOrElse(
                email -> deliver(notification, email),
                () -> LOG.info("email channel skip notificationId={} recipient={} reason=no_email_mapping",
                        notification.notificationId(), recipient));
    }

    private void deliver(Notification notification, String email) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(properties.getFrom());
            helper.setTo(email);
            helper.setSubject(buildSubject(notification));
            helper.setText(buildBody(notification), false);
            mailSender.send(message);
            LOG.info("email channel sent notificationId={} eventType={} recipient={}",
                    notification.notificationId(), notification.eventType(), email);
        } catch (MessagingException ex) {
            LOG.error("email channel send failed notificationId={} recipient={}",
                    notification.notificationId(), email, ex);
            throw new IllegalStateException("email delivery failed", ex);
        }
    }

    private static String buildSubject(Notification notification) {
        return "AIHub: " + notification.eventType();
    }

    private static String buildBody(Notification notification) {
        StringBuilder body = new StringBuilder();
        body.append("Event: ").append(notification.eventType()).append('\n');
        if (notification.i18nKey() != null && !notification.i18nKey().isBlank()) {
            body.append("Message key: ").append(notification.i18nKey()).append('\n');
        }
        if (notification.parameters() != null && !notification.parameters().isBlank()) {
            body.append("Parameters: ").append(notification.parameters()).append('\n');
        }
        return body.toString();
    }
}
