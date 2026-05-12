/**
 * 功能：OpenMetadata 服务层封装
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.integration.openmetadata;

import com.harnessdg.integration.config.IntegrationConfig;
import com.harnessdg.model.ontology.entity.OntDimension;
import com.harnessdg.model.ontology.entity.OntEntity;
import com.harnessdg.model.ontology.entity.OntMetric;
import com.harnessdg.ontology.mapper.OntDimensionMapper;
import com.harnessdg.ontology.mapper.OntEntityMapper;
import com.harnessdg.ontology.mapper.OntMetricMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
    private final OntEntityMapper ontEntityMapper;
    private final OntMetricMapper ontMetricMapper;
    private final OntDimensionMapper ontDimensionMapper;

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

    /**
     * 同步实体到 OpenMetadata
     * 查询实体完整信息，调用 client.registerTable() 注册到 OpenMetadata
     *
     * @param entityId 实体 ID
     */
    public void syncEntityToOpenMetadata(Long entityId) {
        log.info("Syncing entity to OpenMetadata: entityId={}", entityId);

        try {
            OntEntity entity = ontEntityMapper.selectById(entityId);
            if (entity == null) {
                log.warn("Entity not found for sync: entityId={}", entityId);
                return;
            }

            String tableName = entity.getCode();
            String description = extractI18nValue(entity.getDescription());
            String database = entity.getDataDomain() != null ? entity.getDataDomain() : "default";
            String schema = "public";

            List<Map<String, Object>> columns = extractColumnsFromTags(entity.getTags());

            openMetadataClient.registerTable(tableName, description, database, schema, columns)
                    .doOnSuccess(result -> log.info("Entity synced to OpenMetadata successfully: entityId={}, tableName={}", entityId, tableName))
                    .doOnError(error -> log.error("Failed to sync entity to OpenMetadata: entityId={}", entityId, error))
                    .block();
        } catch (Exception e) {
            log.warn("OpenMetadata service unavailable, degraded to log warning: entityId={}", entityId, e);
        }
    }

    /**
     * 同步指标到 OpenMetadata
     * 查询指标完整信息，调用 client.registerMetric() 注册到 OpenMetadata
     *
     * @param metricId 指标 ID
     */
    public void syncMetricToOpenMetadata(Long metricId) {
        log.info("Syncing metric to OpenMetadata: metricId={}", metricId);

        try {
            OntMetric metric = ontMetricMapper.selectById(metricId);
            if (metric == null) {
                log.warn("Metric not found for sync: metricId={}", metricId);
                return;
            }

            String metricName = metric.getCode();
            String description = extractI18nValue(metric.getDescription());
            String entityType = metric.getEntityId() != null ? String.valueOf(metric.getEntityId()) : "unknown";
            String expression = metric.getExpression() != null ? metric.getExpression() : "";
            String unit = metric.getUnit() != null ? metric.getUnit() : "";

            openMetadataClient.registerMetric(metricName, description, entityType, expression, unit)
                    .doOnSuccess(result -> log.info("Metric synced to OpenMetadata successfully: metricId={}, metricName={}", metricId, metricName))
                    .doOnError(error -> log.error("Failed to sync metric to OpenMetadata: metricId={}", metricId, error))
                    .block();
        } catch (Exception e) {
            log.warn("OpenMetadata service unavailable, degraded to log warning: metricId={}", metricId, e);
        }
    }

    /**
     * 从 Pipeline 配置提取并注册血缘关系
     * 查询 Entity 关联的 Metrics 和 Dimensions，根据 Pipeline 配置生成表间映射
     *
     * @param entityId 实体 ID
     */
    public void syncLineageFromPipeline(Long entityId) {
        log.info("Syncing lineage from pipeline: entityId={}", entityId);

        try {
            OntEntity entity = ontEntityMapper.selectById(entityId);
            if (entity == null) {
                log.warn("Entity not found for lineage sync: entityId={}", entityId);
                return;
            }

            String sourceTable = entity.getCode();

            // 查询关联的 Metrics
            LambdaQueryWrapper<OntMetric> metricWrapper = new LambdaQueryWrapper<>();
            metricWrapper.eq(OntMetric::getEntityId, entityId);
            List<OntMetric> metrics = ontMetricMapper.selectList(metricWrapper);

            // 查询关联的 Dimensions
            LambdaQueryWrapper<OntDimension> dimWrapper = new LambdaQueryWrapper<>();
            dimWrapper.eq(OntDimension::getEntityId, entityId);
            List<OntDimension> dimensions = ontDimensionMapper.selectList(dimWrapper);

            // 根据 Metrics 和 Dimensions 生成列映射
            List<Map<String, Object>> columnMappings = new ArrayList<>();

            for (OntMetric metric : metrics) {
                Map<String, Object> mapping = Map.of(
                        "fromColumn", metric.getCode(),
                        "toColumn", metric.getCode(),
                        "transform", metric.getExpression() != null ? metric.getExpression() : "identity"
                );
                columnMappings.add(mapping);
            }

            for (OntDimension dim : dimensions) {
                String tableColumn = dim.getTableColumn() != null ? dim.getTableColumn() : dim.getCode();
                Map<String, Object> mapping = Map.of(
                        "fromColumn", tableColumn,
                        "toColumn", dim.getCode(),
                        "transform", "identity"
                );
                columnMappings.add(mapping);
            }

            // 如果没有列映射，跳过血缘注册
            if (columnMappings.isEmpty()) {
                log.info("No column mappings found for lineage sync: entityId={}", entityId);
                return;
            }

            // 目标表使用 entity 的 code + "_output" 作为默认目标
            String targetTable = sourceTable + "_output";

            openMetadataClient.registerLineage(sourceTable, targetTable, columnMappings)
                    .doOnSuccess(result -> log.info("Lineage synced from pipeline successfully: entityId={}, {} -> {}", entityId, sourceTable, targetTable))
                    .doOnError(error -> log.error("Failed to sync lineage from pipeline: entityId={}", entityId, error))
                    .block();
        } catch (Exception e) {
            log.warn("OpenMetadata service unavailable, degraded to log warning: entityId={}", entityId, e);
        }
    }

    /**
     * 自动打标（敏感等级、数据域、负责人）
     * 从 Entity 的 dataDomain 提取数据域标签，从 owner 提取负责人标签，从 tags 提取敏感等级标签
     *
     * @param entityId 实体 ID
     */
    public void autoTagEntity(Long entityId) {
        log.info("Auto tagging entity: entityId={}", entityId);

        try {
            OntEntity entity = ontEntityMapper.selectById(entityId);
            if (entity == null) {
                log.warn("Entity not found for auto tagging: entityId={}", entityId);
                return;
            }

            List<String> tags = new ArrayList<>();

            // 从 dataDomain 提取数据域标签
            if (entity.getDataDomain() != null && !entity.getDataDomain().isBlank()) {
                tags.add("domain:" + entity.getDataDomain());
            }

            // 从 owner 提取负责人标签
            if (entity.getOwner() != null && !entity.getOwner().isBlank()) {
                tags.add("owner:" + entity.getOwner());
            }

            // 从 tags 提取敏感等级标签
            if (entity.getTags() != null && entity.getTags().containsKey("sensitivity")) {
                Object sensitivity = entity.getTags().get("sensitivity");
                tags.add("sensitivity:" + sensitivity.toString());
            }

            // 如果没有标签，跳过打标
            if (tags.isEmpty()) {
                log.info("No tags to apply for entity: entityId={}", entityId);
                return;
            }

            openMetadataClient.addTags("tables", entity.getCode(), tags)
                    .doOnSuccess(result -> log.info("Entity auto tagged successfully: entityId={}, tags={}", entityId, tags))
                    .doOnError(error -> log.error("Failed to auto tag entity: entityId={}", entityId, error))
                    .block();
        } catch (Exception e) {
            log.warn("OpenMetadata service unavailable, degraded to log warning: entityId={}", entityId, e);
        }
    }

    /**
     * 从 i18n Map 中提取值（取第一个非空值）
     */
    private String extractI18nValue(Map<String, String> i18nMap) {
        if (i18nMap == null || i18nMap.isEmpty()) {
            return "";
        }
        return i18nMap.values().stream()
                .filter(v -> v != null && !v.isBlank())
                .findFirst()
                .orElse("");
    }
}
