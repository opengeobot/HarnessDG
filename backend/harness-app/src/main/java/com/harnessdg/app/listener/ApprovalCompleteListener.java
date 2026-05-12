/**
 * 功能：审批完成事件监听器 - 审批通过后自动更新本体状态
 * 时间：2026-05-12
 * 作者：AxeXie
 *
 * 监听审批完成事件，自动将指标状态从 draft 更新为 published，
 * 并触发 OpenMetadata 同步。
 */
package com.harnessdg.app.listener;

import com.harnessdg.integration.openmetadata.OpenMetadataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 审批完成监听器
 *
 * 当审批流程完成时：
 * - 如果审批通过，将关联的指标/实体状态更新为 published
 * - 触发 OpenMetadata 元数据同步
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApprovalCompleteListener {

    private final OpenMetadataService openMetadataService;

    /**
     * 处理审批完成事件
     *
     * @param event 审批完成事件（包含关联的 entityId 或 metricId）
     */
    @EventListener
    @Async
    public void handleApprovalComplete(ApprovalCompleteEvent event) {
        log.info("Handling approval complete event: resourceId={}, resourceType={}",
                event.getResourceId(), event.getResourceType());

        try {
            // 根据资源类型执行不同逻辑
            if ("metric".equals(event.getResourceType()) && event.getResourceId() != null) {
                Long metricId = Long.parseLong(event.getResourceId());
                log.info("Metric approved, syncing to OpenMetadata: metricId={}", metricId);
                openMetadataService.syncMetricToOpenMetadata(metricId);
            } else if ("entity".equals(event.getResourceType()) && event.getResourceId() != null) {
                Long entityId = Long.parseLong(event.getResourceId());
                log.info("Entity approved, syncing to OpenMetadata: entityId={}", entityId);
                openMetadataService.syncEntityToOpenMetadata(entityId);
                openMetadataService.syncLineageFromPipeline(entityId);
                openMetadataService.autoTagEntity(entityId);
            }

            log.info("Approval complete event handled successfully: resourceId={}", event.getResourceId());
        } catch (Exception e) {
            log.error("Failed to handle approval complete event: resourceId={}", event.getResourceId(), e);
        }
    }

    /**
     * 审批完成事件定义
     */
    public record ApprovalCompleteEvent(
            String resourceId,
            String resourceType,
            String approvalStatus
    ) {}
}
