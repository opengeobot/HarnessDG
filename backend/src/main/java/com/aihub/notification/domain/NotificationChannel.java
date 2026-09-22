/*
 * 功能: 通知渠道端口，定义外发渠道（邮件/IM/Webhook）的统一投递契约。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.notification.domain;

/**
 * 通知外发渠道端口。
 *
 * <p>站内通知同步落库；邮件、IM、Webhook 等异步渠道通过本端口解耦。
 * 实现位于 infrastructure 适配层，可按配置条件装配。
 */
public interface NotificationChannel {

    /** 渠道标识（如 email、webhook、im）。 */
    String channelId();

    /**
     * 向指定收件人投递通知。
     *
     * @param notification 已落库的站内通知（参数已脱敏）
     * @param recipient    外发目标（邮箱地址、Webhook URL、IM 用户 ID 等，由渠道解释）
     */
    void send(Notification notification, String recipient);
}
