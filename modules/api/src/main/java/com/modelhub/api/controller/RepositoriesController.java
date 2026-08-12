package com.modelhub.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modelhub.api.dto.RepoRequests.CreateRepositoryRequest;
import com.modelhub.api.support.IdempotentOps;
import com.modelhub.api.support.Principals;
import com.modelhub.catalog.service.CatalogService;
import com.modelhub.catalog.service.CatalogService.CreateRepoCmd;
import com.modelhub.catalog.service.CatalogService.JobView;
import com.modelhub.catalog.service.CatalogService.RepoView;
import com.modelhub.catalog.service.CatalogService.UpdateRepoCmd;
import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.error.ErrorCode;
import com.modelhub.shared.idempotency.JdbcIdempotencyService.Acquired;
import com.modelhub.shared.paging.PageQuery;
import com.modelhub.shared.paging.PageResult;
import com.modelhub.shared.web.ApiEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 仓库目录端点（04 §2）：列表/resolve 匿名可达；写操作带 Idempotency-Key（04 §10）；
 * PATCH/DELETE 条件更新带 If-Match（04 §10）；DELETE/restore 返回 202 JobEnvelope。
 */
@RestController
@RequestMapping("/api/v1/repositories")
public class RepositoriesController {

    private static final Set<String> UPDATABLE_FIELDS =
            Set.of("displayName", "description", "visibility", "gated", "metadataSchemaVersion", "metadata");

    private final CatalogService catalog;
    private final IdempotentOps idempotent;
    private final ObjectMapper objectMapper;

    public RepositoriesController(CatalogService catalog, IdempotentOps idempotent, ObjectMapper objectMapper) {
        this.catalog = catalog;
        this.idempotent = idempotent;
        this.objectMapper = objectMapper;
    }

    // ---------- 查询 ----------

    @GetMapping
    public ApiEnvelope<PageResult<RepoView>> list(@RequestParam MultiValueMap<String, String> params,
                                                  HttpServletRequest request) {
        return ApiEnvelope.ok(catalog.list(Principals.optionalCurrent(request), params));
    }

    @GetMapping("/{repoId}")
    public ResponseEntity<ApiEnvelope<RepoView>> get(@PathVariable UUID repoId, HttpServletRequest request) {
        RepoView view = catalog.get(Principals.optionalCurrent(request), repoId);
        return ResponseEntity.ok().eTag(view.etag()).body(ApiEnvelope.ok(view));
    }

    @GetMapping("/resolve/{typeKey}/{namespace}/{name}")
    public ResponseEntity<ApiEnvelope<RepoView>> resolve(@PathVariable String typeKey,
                                                         @PathVariable String namespace,
                                                         @PathVariable String name,
                                                         HttpServletRequest request) {
        RepoView view = catalog.resolve(Principals.optionalCurrent(request), typeKey, namespace, name);
        return ResponseEntity.ok().eTag(view.etag()).body(ApiEnvelope.ok(view));
    }

    @GetMapping("/{repoId}/related")
    public ApiEnvelope<PageResult<RepoView>> related(@PathVariable UUID repoId,
                                                     @RequestParam(required = false) String type,
                                                     @RequestParam Map<String, String> params,
                                                     HttpServletRequest request) {
        PageQuery page = PageQuery.from(params);
        return ApiEnvelope.ok(catalog.related(Principals.optionalCurrent(request), repoId, type, page));
    }

    // ---------- 创建（Idempotency-Key，05 §8 Saga 入口） ----------

    @PostMapping
    public ResponseEntity<String> create(@Valid @RequestBody CreateRepositoryRequest body,
                                         @RequestHeader("Idempotency-Key") String idempotencyKey,
                                         HttpServletRequest request) throws Exception {
        JsonNode hashSource = objectMapper.valueToTree(body);
        Acquired acq = idempotent.begin("repository.create", idempotencyKey, hashSource);
        if (acq.replay()) {
            return ResponseEntity.status(acq.recordedStatus())
                    .contentType(MediaType.APPLICATION_JSON).body(acq.recordedBody());
        }
        String metadataJson = body.metadata() == null || body.metadata().isNull()
                ? "{}" : objectMapper.writeValueAsString(body.metadata());
        CreateRepoCmd cmd = new CreateRepoCmd(body.namespaceId(), body.type(), body.metadataSchemaVersion(),
                body.name(), body.displayName(), body.description(), body.visibility(), body.gated(),
                metadataJson);
        RepoView view = catalog.create(Principals.requireCurrent(request), cmd);
        String responseBody = objectMapper.writeValueAsString(ApiEnvelope.created(view));
        idempotent.finish("repository.create", idempotencyKey, 201, responseBody);
        return ResponseEntity.status(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON).body(responseBody);
    }

    // ---------- PATCH（If-Match，04 §10） ----------

    @PatchMapping("/{repoId}")
    public ResponseEntity<ApiEnvelope<RepoView>> update(@PathVariable UUID repoId,
                                                        @RequestBody JsonNode body,
                                                        @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                                        HttpServletRequest request) throws Exception {
        UpdateRepoCmd cmd = toUpdateCmd(body);
        RepoView view = catalog.update(Principals.requireCurrent(request), repoId, cmd, ifMatch);
        return ResponseEntity.ok().eTag(view.etag()).body(ApiEnvelope.ok(view));
    }

    /** 契约 UpdateRepositoryRequest：minProperties 1、additionalProperties false、dependentRequired。 */
    private UpdateRepoCmd toUpdateCmd(JsonNode body) throws Exception {
        if (body == null || !body.isObject() || body.isEmpty()) {
            throw ApiException.badRequest("更新请求至少需要一个字段",
                    List.of(new ApiException.Detail("body", "min_properties")));
        }
        body.fieldNames().forEachRemaining(f -> {
            if (!UPDATABLE_FIELDS.contains(f)) {
                throw ApiException.badRequest("未知更新字段: " + f,
                        List.of(new ApiException.Detail(f, "additional_property")));
            }
        });
        if (body.has("metadata") && !body.has("metadataSchemaVersion")) {
            throw new ApiException(ErrorCode.METADATA_SCHEMA_INVALID, "更新 metadata 必须同时提交 metadataSchemaVersion",
                    List.of(new ApiException.Detail("metadata", "requires_schema_version")));
        }
        String metadataJson = body.has("metadata") && !body.get("metadata").isNull()
                ? objectMapper.writeValueAsString(body.get("metadata")) : null;
        Integer schemaVersion = body.has("metadataSchemaVersion")
                ? body.get("metadataSchemaVersion").asInt() : null;
        String visibility = body.has("visibility") && !body.get("visibility").isNull()
                ? body.get("visibility").asText() : null;
        Boolean gated = body.has("gated") && !body.get("gated").isNull()
                ? body.get("gated").asBoolean() : null;
        return new UpdateRepoCmd(
                body.has("displayName") ? textOrNull(body.get("displayName")) : null,
                body.has("description") ? textOrNull(body.get("description")) : null,
                visibility, gated, schemaVersion, metadataJson,
                body.has("displayName"), body.has("description"));
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    // ---------- DELETE / restore（202 JobEnvelope） ----------

    @DeleteMapping("/{repoId}")
    public ResponseEntity<String> delete(@PathVariable UUID repoId,
                                         @RequestHeader("Idempotency-Key") String idempotencyKey,
                                         @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                         HttpServletRequest request) throws Exception {
        Acquired acq = idempotent.begin("repository.delete", idempotencyKey, null);
        if (acq.replay()) {
            return ResponseEntity.status(acq.recordedStatus())
                    .contentType(MediaType.APPLICATION_JSON).body(acq.recordedBody());
        }
        JobView job = catalog.delete(Principals.requireCurrent(request), repoId, ifMatch);
        String responseBody = objectMapper.writeValueAsString(ApiEnvelope.ok(job));
        idempotent.finish("repository.delete", idempotencyKey, 202, responseBody);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .contentType(MediaType.APPLICATION_JSON).body(responseBody);
    }

    @PostMapping("/{repoId}:restore")
    public ResponseEntity<String> restore(@PathVariable UUID repoId,
                                          @RequestHeader("Idempotency-Key") String idempotencyKey,
                                          HttpServletRequest request) throws Exception {
        Acquired acq = idempotent.begin("repository.restore", idempotencyKey, null);
        if (acq.replay()) {
            return ResponseEntity.status(acq.recordedStatus())
                    .contentType(MediaType.APPLICATION_JSON).body(acq.recordedBody());
        }
        JobView job = catalog.restore(Principals.requireCurrent(request), repoId);
        String responseBody = objectMapper.writeValueAsString(ApiEnvelope.ok(job));
        idempotent.finish("repository.restore", idempotencyKey, 202, responseBody);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .contentType(MediaType.APPLICATION_JSON).body(responseBody);
    }
}
