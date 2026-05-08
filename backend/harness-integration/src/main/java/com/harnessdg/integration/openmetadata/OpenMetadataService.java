/**
 * 功能：OpenMetadata 服务层封装
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.integration.openmetadata;

import com.harnessdg.integration.config.IntegrationConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenMetadata 服务
 * 提供高层业务逻辑封装，支持本体发布时自动注册元数据和血缘
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "harnessdg.integration.open-metadata.enabled", havingValue = "true", matchIfMissing = false)
public class OpenMetadataService {

    private final OpenMetadataClient openMetadataClient;
    private final IntegrationConfig integrationConfig;

    /**
     * 注册实体元数据
     */
    public Mono<Map> registerEntity(String entityCode, String entityName, String description,
                                     String tableName, String domain, Map<String, Object> tags) {
        log.info("Registering entity metadata: {}", entityCode);

        List<Map<String, Object>> columns = extractColumnsFromTags(tags);

        return openMetadataClient.registerTable(
                tableName != null ? tableName : entityCode,
                description,
                domain != null ? domain : "default",
                "public",
                columns
        ).doOnSuccess(result -> log.info("Entity registered: {}", entityCode))
         .doOnError(error -> log.error("Failed to register entity: {}", entityCode, error));
    }

    /**
     * 注册指标元数据
     */
    public Mono<Map> registerMetric(String metricCode, String metricName, String description,
                                     String entityCode, String expression, String unit) {
        log.info("Registering metric metadata: {}", metricCode);

        return openMetadataClient.registerMetric(
                metricCode,
                description,
                entityCode,
                expression,
                unit
        ).doOnSuccess(result -> log.info("Metric registered: {}", metricCode))
         .doOnError(error -> log.error("Failed to register metric: {}", metricCode, error));
    }

    /**
     * 注册血缘关系
     */
    public Mono<Map> registerLineage(String sourceTable, String targetTable,
                                      List<Map<String, Object>> columnMappings) {
        log.info("Registering lineage: {} -> {}", sourceTable, targetTable);

        return openMetadataClient.registerLineage(sourceTable, targetTable, columnMappings)
                .doOnSuccess(result -> log.info("Lineage registered: {} -> {}", sourceTable, targetTable))
                .doOnError(error -> log.error("Failed to register lineage", error));
    }

    /**
     * 为实体添加标签
     */
    public Mono<Map> addEntityTags(String entityId, List<String> tags) {
        return openMetadataClient.addTags("tables", entityId, tags);
    }

    /**
     * 从本体 tags 中提取列定义
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractColumnsFromTags(Map<String, Object> tags) {
        List<Map<String, Object>> columns = new ArrayList<>();

        if (tags == null || !tags.containsKey("fields")) {
            return columns;
        }

        Object fieldsObj = tags.get("fields");
        if (fieldsObj instanceof List) {
            List<Map<String, Object>> fields = (List<Map<String, Object>>) fieldsObj;
            for (Map<String, Object> field : fields) {
                Map<String, Object> column = Map.of(
                        "name", field.getOrDefault("name", ""),
                        "dataType", field.getOrDefault("type", "STRING"),
                        "description", field.getOrDefault("description", ""),
                        "nullable", !Boolean.TRUE.equals(field.get("not_null")),
                        "primaryKey", Boolean.TRUE.equals(field.get("is_primary_key"))
                );
                columns.add(column);
            }
        }

        return columns;
    }
}
