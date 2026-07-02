/*
 * 功能: job→notification 桥接适配器——实现 JobNotificationPort，任务进入 DEAD 时发送站内通知。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.notification.application;

import com.aihub.job.application.JobNotificationPort;
import com.aihub.notification.domain.NotificationSeverity;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * job→notification 桥接适配器。
 *
 * <p>实现 {@link JobNotificationPort}，任务进入 DEAD 时调用 {@link NotificationService} 发送站内通知。
 * 若 principalId 为空则发至 "system" 主体。失败不回滚核心事务（通知为旁路，不应阻塞任务流程）。
 */
@Component
public class JobNotificationAdapter implements JobNotificationPort {

    private static final Logger LOG = LoggerFactory.getLogger(JobNotificationAdapter.class);

    private final NotificationService notificationService;

    public JobNotificationAdapter(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void notifyJobDead(String jobId, String jobType, int retryCount, String errorCode, String principalId) {
        String recipient = principalId != null && !principalId.isBlank() ? principalId : "system";
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("jobId", jobId);
        params.put("jobType", jobType);
        params.put("retryCount", retryCount);
        params.put("errorCode", errorCode);
        try {
            notificationService.sendInAppNotification(
                    recipient,
                    "JOB_DEAD",
                    "notification.job.dead.title",
                    NotificationSeverity.ERROR,
                    params);
        } catch (Exception ex) {
            // 通知失败不能阻塞任务流程；结构化日志告警。
            LOG.error("failed to send JOB_DEAD notification jobId={} error={}", jobId, ex.getMessage(), ex);
        }
    }
}
