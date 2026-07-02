/*
 * 功能: 通知仓储端口，定义站内通知创建、查询与标记已读能力。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.notification.domain;

import java.time.Instant;
import java.util.List;

/**
 * 通知仓储端口。
 *
 * <p>领域端口不感知 SQL/MyBatis，由基础设施层提供 JDBC 实现。
 */
public interface NotificationRepository {

    /** 创建站内通知。 */
    void insert(Notification notification);

    /** 按业务 ID 查找通知。 */
    Notification findByNotificationId(String notificationId);

    /**
     * 游标分页查询当前主体的通知（按 created_at, id 降序——最新优先）。
     *
     * @param principalId  目标主体 ID
     * @param unreadOnly   仅未读
     * @param cursorTime   游标创建时间（可空）
     * @param cursorId     游标内部 ID（可空）
     * @param limit        每页条数
     * @return 该页通知
     */
    List<Notification> listByPrincipal(String principalId, boolean unreadOnly,
                                       Instant cursorTime, Long cursorId, int limit);

    /** 标记通知已读（仅当属于当前主体且未读时更新）。返回是否实际更新。 */
    boolean markRead(String notificationId, String principalId, Instant now);
}
