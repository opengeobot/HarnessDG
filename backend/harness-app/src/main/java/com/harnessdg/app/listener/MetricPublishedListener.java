/**
 * 功能：指标发布事件监听器 - 指标发布后自动生成质量规则
 * 时间：2026-05-12
 * 作者：AxeXie
 *
 * 监听指标发布事件，自动为发布的指标生成质量规则并执行校验。
 */
package com.harnessdg.app.listener;

import com.harnessdg.quality.service.QualityRuleService;
import com.harnessdg.model.quality.dto.QualityRuleAutoGenerateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 指标发布监听器
 *
 * 当指标状态变为 published 时：
 * - 自动生成质量规则（基于指标字段元数据）
 * - 触发质量检查
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MetricPublishedListener {

    private final QualityRuleService qualityRuleService;

    /**
     * 处理指标发布事件
     *
     * @param event 指标发布事件（包含 metricId）
     */
    @EventListener
    @Async
    public void handleMetricPublished(MetricPublishedEvent event) {
        log.info("Handling metric published event: metricId={}", event.metricId());

        try {
            // 自动生成质量规则
            QualityRuleAutoGenerateRequest request = new QualityRuleAutoGenerateRequest();
            request.setMetricId(event.metricId());

            var generatedRules = qualityRuleService.autoGenerateRules(request);
            log.info("Auto-generated {} quality rules for metric: metricId={}",
                    generatedRules.size(), event.metricId());

            // TODO: 后续可在此处触发质量检查执行
            // for (var rule : generatedRules) {
            //     qualityRuleService.runCheck(rule.getId());
            // }

            log.info("Metric published event handled successfully: metricId={}", event.metricId());
        } catch (Exception e) {
            log.error("Failed to handle metric published event: metricId={}", event.metricId(), e);
        }
    }

    /**
     * 指标发布事件定义
     */
    public record MetricPublishedEvent(
            Long metricId,
            String metricCode,
            String status
    ) {}
}
