package com.modelhub.catalog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modelhub.catalog.domain.ResourceTypeEntity;
import com.modelhub.catalog.domain.SchemaVersionEntity;
import com.modelhub.catalog.domain.SchemaVersionId;
import com.modelhub.catalog.repo.ResourceTypeRepository;
import com.modelhub.catalog.repo.SchemaVersionRepository;
import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 资源类型注册表查询（03 §2.2、契约 ResourceType/ResourceTypeSchema）：
 * 匿名可读；defaultVisibility/allowedWorkflows/filePolicy 来自当前 schema 版本。
 */
@Service
public class ResourceTypesService {

    /** 契约 ResourceType schema。 */
    public record ResourceTypeView(String typeKey, String displayName, int currentSchemaVersion,
                                   List<String> capabilities, String rendererKey, String fallbackRenderer,
                                   String defaultVisibility, List<String> allowedWorkflows,
                                   Object filePolicy, String status) {}

    /** 契约 ResourceTypeSchema schema。 */
    public record ResourceTypeSchemaView(String typeKey, int version, Object metadataSchema, Object uiSchema,
                                         Object filePolicy, String defaultVisibility,
                                         List<String> allowedWorkflows, List<Object> facets,
                                         String checksum, String status, String publishedAt) {}

    private final ResourceTypeRepository resourceTypes;
    private final SchemaVersionRepository schemaVersions;
    private final ObjectMapper objectMapper;

    public ResourceTypesService(ResourceTypeRepository resourceTypes, SchemaVersionRepository schemaVersions,
                                ObjectMapper objectMapper) {
        this.resourceTypes = resourceTypes;
        this.schemaVersions = schemaVersions;
        this.objectMapper = objectMapper;
    }

    /** 列出所有可用类型（disabled 不返回；deprecated 保留供旧仓库展示）。 */
    @Transactional(readOnly = true)
    public List<ResourceTypeView> list() {
        return resourceTypes.findAll().stream()
                .filter(t -> !"disabled".equals(t.getStatus()))
                .map(this::toView)
                .toList();
    }

    /** schema 详情：version 缺省取 currentSchemaVersion。 */
    @Transactional(readOnly = true)
    public ResourceTypeSchemaView schema(String typeKey, Integer version) {
        ResourceTypeEntity type = loadType(typeKey);
        int v = version == null ? type.getCurrentSchemaVersion() : version;
        SchemaVersionEntity schema = schemaVersions
                .findById(new SchemaVersionId(typeKey, v))
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "schema 版本不存在: " + typeKey + "@" + v));
        return new ResourceTypeSchemaView(typeKey, v, parse(schema.getMetadataSchema()),
                parse(schema.getUiSchema()), parse(schema.getFilePolicy()), schema.getDefaultVisibility(),
                stringList(schema.getAllowedWorkflows()), facetsOf(schema), schema.getChecksum(),
                schema.getStatus(),
                schema.getPublishedAt() == null ? null : schema.getPublishedAt().toString());
    }

    private ResourceTypeEntity loadType(String typeKey) {
        ResourceTypeEntity type = resourceTypes.findById(typeKey)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "未知资源类型: " + typeKey));
        if ("disabled".equals(type.getStatus())) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "资源类型已停用: " + typeKey);
        }
        return type;
    }

    private ResourceTypeView toView(ResourceTypeEntity type) {
        SchemaVersionEntity schema = type.getCurrentSchemaVersion() == null ? null
                : schemaVersions.findById(new SchemaVersionId(type.getTypeKey(), type.getCurrentSchemaVersion()))
                        .orElse(null);
        return new ResourceTypeView(type.getTypeKey(), type.getDisplayName(),
                type.getCurrentSchemaVersion() == null ? 1 : type.getCurrentSchemaVersion(),
                stringList(type.getCapabilities()), type.getRendererKey(), "generic_metadata",
                schema == null ? "public" : schema.getDefaultVisibility(),
                schema == null ? List.of() : stringList(schema.getAllowedWorkflows()),
                schema == null ? null : parse(schema.getFilePolicy()), type.getStatus());
    }

    /** facet_definitions → 契约 facets（key/dataType/filterable/sortable）。 */
    private List<Object> facetsOf(SchemaVersionEntity schema) {
        JsonNode defs = parseNode(schema.getFacetDefinitions());
        List<Object> facets = new ArrayList<>();
        if (defs == null || !defs.isArray()) {
            return facets;
        }
        for (JsonNode def : defs) {
            facets.add(objectMapper.createObjectNode()
                    .put("key", def.path("key").asText())
                    .put("dataType", def.path("type").asText("text"))
                    .put("filterable", def.path("filterable").asBoolean(false))
                    .put("sortable", def.path("sortable").asBoolean(false))
                    .put("taxonomy", def.path("taxonomy").isNull() ? null : def.path("taxonomy").asText()));
        }
        return facets;
    }

    private Object parse(String json) {
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    private JsonNode parseNode(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> stringList(String json) {
        JsonNode node = parseNode(json);
        List<String> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(n -> out.add(n.asText()));
        }
        return out;
    }
}
