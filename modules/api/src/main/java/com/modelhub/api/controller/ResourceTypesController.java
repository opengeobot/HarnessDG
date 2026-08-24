package com.modelhub.api.controller;

import com.modelhub.catalog.service.MetadataOptionsService;
import com.modelhub.catalog.service.MetadataOptionsService.MetadataOptionsView;
import com.modelhub.catalog.service.ResourceTypesService;
import com.modelhub.catalog.service.ResourceTypesService.ResourceTypeSchemaView;
import com.modelhub.shared.web.ApiEnvelope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 资源类型与 metadata 选项端点（04 §3）：匿名可读，
 * 客户端据此发现类型、schema 与受控词表，禁止硬编码。
 * 列表响应为契约 ResourceTypeListEnvelope：data.items 数组。
 */
@RestController
public class ResourceTypesController {

    private final ResourceTypesService resourceTypes;
    private final MetadataOptionsService metadataOptions;

    public ResourceTypesController(ResourceTypesService resourceTypes, MetadataOptionsService metadataOptions) {
        this.resourceTypes = resourceTypes;
        this.metadataOptions = metadataOptions;
    }

    @GetMapping("/api/v1/resource-types")
    public ApiEnvelope<Map<String, Object>> list() {
        return ApiEnvelope.ok(Map.of("items", resourceTypes.list()));
    }

    @GetMapping("/api/v1/resource-types/{typeKey}/schema")
    public ApiEnvelope<ResourceTypeSchemaView> schema(@PathVariable String typeKey,
                                                      @RequestParam(required = false) Integer version) {
        return ApiEnvelope.ok(resourceTypes.schema(typeKey, version));
    }

    @GetMapping("/api/v1/metadata/options")
    public ApiEnvelope<MetadataOptionsView> options() {
        return ApiEnvelope.ok(metadataOptions.options());
    }
}
