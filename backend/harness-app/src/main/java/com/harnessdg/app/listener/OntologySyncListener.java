/**
 * 功能：本体同步监听器 - 监听 OntologyCreatedEvent 并异步同步到 OpenMetadata
 * 时间：2026-05-12
 * 作者：AxeXie
 */
package com.harnessdg.app.listener;

import com.harnessdg.integration.openmetadata.OpenMetadataService;
import com.harnessdg.ontology.service.impl.OntologyServiceImpl.OntologyCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 本体创建事件监听器
 * 监听 OntologyCreatedEvent，异步将实体/指标同步到 OpenMetadata
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OntologySyncListener {

    private final ObjectProvider<OpenMetadataService> openMetadataServiceProvider;

    /**
     * 处理本体创建事件
     * 异步同步实体和指标到 OpenMetadata
     *
     * @param event 本体创建事件
     */
    @EventListener
    @Async
    public void handleOntologyCreated(OntologyCreatedEvent event) {
        log.info("Received OntologyCreatedEvent: entityId={}, metricId={}, entityType={}",
                event.entityId(), event.metricId(), event.entityType());

        OpenMetadataService openMetadataService = openMetadataServiceProvider.getIfAvailable();
        if (openMetadataService == null) {
            log.info("OpenMetadata integration disabled, skip ontology sync: entityId={}, metricId={}",
                    event.entityId(), event.metricId());
            return;
        }

        // 同步实体
        if (event.entityId() != null) {
            try {
                openMetadataService.syncEntityToOpenMetadata(event.entityId());
                // 同步血缘关系
                openMetadataService.syncLineageFromPipeline(event.entityId());
                // 自动打标
                openMetadataService.autoTagEntity(event.entityId());
            } catch (Exception e) {
                log.warn("Failed to sync entity to OpenMetadata: entityId={}", event.entityId(), e);
            }
        }

        // 同步指标
        if (event.metricId() != null) {
            try {
                openMetadataService.syncMetricToOpenMetadata(event.metricId());
            } catch (Exception e) {
                log.warn("Failed to sync metric to OpenMetadata: metricId={}", event.metricId(), e);
            }
        }
    }
}
