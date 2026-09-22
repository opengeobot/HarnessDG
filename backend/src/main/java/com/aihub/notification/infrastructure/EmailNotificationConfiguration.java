/*
 * 功能: 邮件通知渠道装配——按配置创建 JavaMailSender。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.notification.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;

/**
 * 邮件渠道 SMTP 装配。
 *
 * <p>仅在 {@code aihub.notification.email.enabled=true} 时装配 {@link JavaMailSender}。
 * SMTP 凭据通过环境变量注入，禁止入 Git。
 */
@Configuration
@ConditionalOnProperty(name = "aihub.notification.email.enabled", havingValue = "true")
@EnableConfigurationProperties(EmailChannelProperties.class)
public class EmailNotificationConfiguration {

    @Bean
    public JavaMailSender notificationJavaMailSender(EmailChannelProperties properties) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(properties.getHost());
        mailSender.setPort(properties.getPort());
        if (StringUtils.hasText(properties.getUsername())) {
            mailSender.setUsername(properties.getUsername());
        }
        if (StringUtils.hasText(properties.getPassword())) {
            mailSender.setPassword(properties.getPassword());
        }
        mailSender.getJavaMailProperties().put("mail.smtp.auth", String.valueOf(properties.isAuth()));
        mailSender.getJavaMailProperties().put("mail.smtp.starttls.enable",
                String.valueOf(properties.isStartTls()));
        return mailSender;
    }
}
