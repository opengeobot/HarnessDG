/*
 * 功能: 受控标签管理 REST 适配器，提供标签列表/创建/更新/启用/停用接口，拒绝自由标签。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.taxonomy.tag.api;

import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.job.application.IdempotencyService;
import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.idempotency.IdempotencyKey;
import com.aihub.shared.idempotency.IdempotencySupport;
import com.aihub.taxonomy.api.TaxonomyApiContext;
import com.aihub.taxonomy.domain.TaxonomyStatus;
import com.aihub.taxonomy.tag.api.TagRequests.CreateTagRequest;
import com.aihub.taxonomy.tag.api.TagRequests.TagStatusRequest;
import com.aihub.taxonomy.tag.api.TagRequests.UpdateTagRequest;
import com.aihub.taxonomy.tag.application.TagApplicationService;
import com.aihub.taxonomy.tag.application.TagDtos.CreateTagCommand;
import com.aihub.taxonomy.tag.application.TagDtos.TagView;
import com.aihub.taxonomy.tag.application.TagDtos.UpdateTagCommand;
import com.aihub.taxonomy.tag.domain.TagScopeType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 受控标签管理 REST 适配器。
 *
 * <p>授权判定走统一 {@link AuthorizationService}（fail-closed）：读列表需 {@code tag:read}，
 * 创建/更新/启用/停用需 {@code tag:manage}。资产写接口只接受已登记 tagId，自由标签在
 * {@link com.aihub.taxonomy.tag.application.TagValidationService} 处被拒绝。
 */
@RestController
@RequestMapping("/api/v1/system/tags")
public class TagController {

    private final TagApplicationService tagService;
    private final AuthorizationService authorizationService;
    private final IdempotencyService idempotencyService;
    private final IdempotencySupport idempotency;

    public TagController(TagApplicationService tagService,
                          AuthorizationService authorizationService,
                          IdempotencyService idempotencyService,
                          ObjectMapper objectMapper) {
        this.tagService = tagService;
        this.authorizationService = authorizationService;
        this.idempotencyService = idempotencyService;
        this.idempotency = new IdempotencySupport(objectMapper);
    }

    /**
     * 查询平台/组织受控标签。
     */
    @GetMapping
    public ApiResponse<List<TagView>> listTags(@RequestParam(required = false) TagScopeType scopeType,
                                               @RequestParam(required = false) String scopeId,
                                               @RequestParam(required = false) TaxonomyStatus status,
                                               @RequestParam(required = false) String keyword) {
        authorizationService.requirePermission(Permissions.TAG_READ);
        return TaxonomyApiContext.respond(tagService.listTags(scopeType, scopeId, status, keyword));
    }

    /**
     * 创建受控标签。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TagView> createTag(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyValue,
            @RequestBody CreateTagRequest request) {
        authorizationService.requirePermission(Permissions.TAG_MANAGE);
        if (request == null) {
            throw new ValidationException("request body is required");
        }
        IdempotencyKey key = idempotency.buildKey(idempotencyKeyValue,
                TaxonomyApiContext.principalId(), "POST", "/api/v1/system/tags");
        String fingerprint = idempotency.sha256Digest(request);
        AtomicReference<TagView> ref = new AtomicReference<>();
        idempotencyService.execute(key, fingerprint, () -> {
            TagView view = tagService.createTag(
                    new CreateTagCommand(request.scopeType(), request.scopeId(), request.tagCode(),
                            request.displayName(), request.i18nKey(), request.color()),
                    TaxonomyApiContext.principalId());
            ref.set(view);
            return new IdempotencyService.IdempotencyResponse(201, idempotency.serialize(view));
        });
        return TaxonomyApiContext.respond(ref.get());
    }

    /**
     * 更新受控标签。
     */
    @PatchMapping("/{tagId}")
    public ApiResponse<TagView> updateTag(
            @PathVariable String tagId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyValue,
            @RequestBody UpdateTagRequest request) {
        authorizationService.requirePermission(Permissions.TAG_MANAGE);
        if (request == null) {
            throw new ValidationException("request body is required");
        }
        IdempotencyKey key = idempotency.buildKey(idempotencyKeyValue,
                TaxonomyApiContext.principalId(), "PATCH", "/api/v1/system/tags/" + tagId);
        String fingerprint = idempotency.sha256Digest(request);
        AtomicReference<TagView> ref = new AtomicReference<>();
        idempotencyService.execute(key, fingerprint, () -> {
            TagView view = tagService.updateTag(tagId,
                    new UpdateTagCommand(request.displayName(), request.i18nKey(),
                            request.color(), request.expectedVersion()),
                    TaxonomyApiContext.principalId());
            ref.set(view);
            return new IdempotencyService.IdempotencyResponse(200, idempotency.serialize(view));
        });
        return TaxonomyApiContext.respond(ref.get());
    }

    /**
     * 启用受控标签。
     */
    @PostMapping("/{tagId}:enable")
    public ApiResponse<Void> enableTag(
            @PathVariable String tagId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyValue,
            @RequestBody(required = false) TagStatusRequest request) {
        authorizationService.requirePermission(Permissions.TAG_MANAGE);
        IdempotencyKey key = idempotency.buildKey(idempotencyKeyValue,
                TaxonomyApiContext.principalId(), "POST", "/api/v1/system/tags/" + tagId + ":enable");
        String fingerprint = request != null ? idempotency.sha256Digest(request) : null;
        idempotencyService.execute(key, fingerprint, () -> {
            tagService.enableTag(tagId, request == null ? null : request.expectedVersion(),
                    TaxonomyApiContext.principalId());
            return new IdempotencyService.IdempotencyResponse(200, "");
        });
        return TaxonomyApiContext.respond(null);
    }

    /**
     * 停用受控标签，保留历史关联回显。
     */
    @PostMapping("/{tagId}:disable")
    public ApiResponse<Void> disableTag(
            @PathVariable String tagId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyValue,
            @RequestBody(required = false) TagStatusRequest request) {
        authorizationService.requirePermission(Permissions.TAG_MANAGE);
        IdempotencyKey key = idempotency.buildKey(idempotencyKeyValue,
                TaxonomyApiContext.principalId(), "POST", "/api/v1/system/tags/" + tagId + ":disable");
        String fingerprint = request != null ? idempotency.sha256Digest(request) : null;
        idempotencyService.execute(key, fingerprint, () -> {
            tagService.disableTag(tagId, request == null ? null : request.expectedVersion(),
                    TaxonomyApiContext.principalId());
            return new IdempotencyService.IdempotencyResponse(200, "");
        });
        return TaxonomyApiContext.respond(null);
    }
}
