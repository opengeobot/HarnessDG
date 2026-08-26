package com.modelhub.catalog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modelhub.catalog.domain.SchemaVersionEntity;
import com.modelhub.identity.domain.SysDictItemEntity;
import com.modelhub.identity.repo.SysDictItemRepository;
import com.modelhub.identity.repo.SysDictRepository;
import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * metadata JSON 校验（04 §6.4）：按资源类型对应版本的 metadataSchema 校验；
 * 失败返回 422 METADATA_SCHEMA_INVALID 与字段级 details。
 * v1 实现 JSON Schema 的稳定子集：required、properties、type、enum、
 * maxLength、maxItems、minimum、additionalProperties。
 */
@Component
public class MetadataValidator {

    private final ObjectMapper objectMapper;
    private final SysDictRepository dicts;
    private final SysDictItemRepository dictItems;

    public MetadataValidator(ObjectMapper objectMapper, SysDictRepository dicts,
                             SysDictItemRepository dictItems) {
        this.objectMapper = objectMapper;
        this.dicts = dicts;
        this.dictItems = dictItems;
    }

    /** 校验并返回解析后的 metadata 根节点。 */
    public JsonNode validate(SchemaVersionEntity schemaVersion, String metadataJson) {
        JsonNode metadata;
        try {
            metadata = objectMapper.readTree(metadataJson);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.METADATA_SCHEMA_INVALID, "metadata 不是合法 JSON",
                    List.of(new ApiException.Detail("metadata", "invalid_json")));
        }
        if (metadata == null || !metadata.isObject()) {
            throw new ApiException(ErrorCode.METADATA_SCHEMA_INVALID, "metadata 必须是对象",
                    List.of(new ApiException.Detail("metadata", "must_be_object")));
        }
        JsonNode schema;
        try {
            schema = objectMapper.readTree(schemaVersion.getMetadataSchema());
        } catch (Exception e) {
            throw new ApiException(ErrorCode.METADATA_SCHEMA_INVALID, "资源类型 schema 不可用");
        }

        List<ApiException.Detail> details = new ArrayList<>();
        JsonNode properties = schema.path("properties");

        // required 检查
        for (JsonNode req : schema.path("required")) {
            String field = req.asText();
            if (!metadata.has(field) || metadata.get(field).isNull()) {
                details.add(new ApiException.Detail(field, "required"));
            }
        }

        // 逐字段校验
        Set<String> known = new HashSet<>();
        properties.fieldNames().forEachRemaining(known::add);
        Iterator<Map.Entry<String, JsonNode>> fields = metadata.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String field = entry.getKey();
            JsonNode value = entry.getValue();
            if (!known.contains(field)) {
                if (!schema.path("additionalProperties").asBoolean(false)) {
                    details.add(new ApiException.Detail(field, "unknown_property"));
                }
                continue;
            }
            checkField(field, value, properties.get(field), details);
        }

        if (!details.isEmpty()) {
            throw new ApiException(ErrorCode.METADATA_SCHEMA_INVALID, "metadata 不符合资源类型 schema", details);
        }
        return metadata;
    }

    private void checkField(String field, JsonNode value, JsonNode prop, List<ApiException.Detail> details) {
        if (value.isNull()) {
            return;
        }
        String type = prop.path("type").asText("string");
        switch (type) {
            case "string" -> {
                if (!value.isTextual()) {
                    details.add(new ApiException.Detail(field, "type_mismatch"));
                    return;
                }
                int maxLen = prop.path("maxLength").asInt(Integer.MAX_VALUE);
                if (value.asText().length() > maxLen) {
                    details.add(new ApiException.Detail(field, "too_long"));
                }
                checkEnum(field, value.asText(), prop, details);
                checkTaxonomy(field, value.asText(), prop, details);
            }
            case "integer" -> {
                if (!value.isIntegralNumber()) {
                    details.add(new ApiException.Detail(field, "type_mismatch"));
                    return;
                }
                if (prop.has("minimum") && value.asLong() < prop.path("minimum").asLong()) {
                    details.add(new ApiException.Detail(field, "below_minimum"));
                }
            }
            case "number" -> {
                if (!value.isNumber()) {
                    details.add(new ApiException.Detail(field, "type_mismatch"));
                }
            }
            case "boolean" -> {
                if (!value.isBoolean()) {
                    details.add(new ApiException.Detail(field, "type_mismatch"));
                }
            }
            case "array" -> {
                if (!value.isArray()) {
                    details.add(new ApiException.Detail(field, "type_mismatch"));
                    return;
                }
                int maxItems = prop.path("maxItems").asInt(Integer.MAX_VALUE);
                if (value.size() > maxItems) {
                    details.add(new ApiException.Detail(field, "too_many_items"));
                }
                JsonNode items = prop.path("items");
                for (int i = 0; i < value.size(); i++) {
                    JsonNode item = value.get(i);
                    if (items.path("type").asText("string").equals("string")) {
                        if (!item.isTextual()) {
                            details.add(new ApiException.Detail(field + "[" + i + "]", "type_mismatch"));
                            continue;
                        }
                        int itemMax = items.path("maxLength").asInt(Integer.MAX_VALUE);
                        if (item.asText().length() > itemMax) {
                            details.add(new ApiException.Detail(field + "[" + i + "]", "too_long"));
                        }
                        checkEnum(field + "[" + i + "]", item.asText(), items, details);
                        checkTaxonomy(field + "[" + i + "]", item.asText(), prop, details);
                    }
                }
            }
            default -> {
                // object 等复杂类型 v1 不做深度校验
            }
        }
    }

    private void checkEnum(String field, String value, JsonNode prop, List<ApiException.Detail> details) {
        JsonNode enumNode = prop.path("enum");
        if (!enumNode.isArray() || enumNode.isEmpty()) {
            return;
        }
        for (JsonNode allowed : enumNode) {
            if (allowed.asText().equals(value)) {
                return;
            }
        }
        details.add(new ApiException.Detail(field, "unknown_value"));
    }

    /** taxonomy 绑定字段：值必须是对应字典的已注册字典项（字典统一后读 sys_dict，active+disabled 均算已注册，保持「弃用值可用」语义）。 */
    private void checkTaxonomy(String field, String value, JsonNode prop, List<ApiException.Detail> details) {
        String taxonomyKey = prop.path("taxonomy").asText(null);
        if (taxonomyKey == null) {
            return;
        }
        dicts.findByDictCode(taxonomyKey).ifPresent(dict -> {
            boolean matched = dictItems.findByDictIdOrderBySortOrder(dict.getId()).stream()
                    .map(SysDictItemEntity::getItemValue)
                    .anyMatch(value::equals);
            if (!matched) {
                details.add(new ApiException.Detail(field, "unknown_taxonomy_value"));
            }
        });
    }
}
