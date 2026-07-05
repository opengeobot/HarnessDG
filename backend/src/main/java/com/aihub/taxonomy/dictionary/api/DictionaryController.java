/*
 * 功能: 字典管理 REST 适配器，提供字典类型列表、字典项列表/新建/更新（含停用）接口。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.taxonomy.dictionary.api;

import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.job.application.IdempotencyService;
import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.idempotency.IdempotencyKey;
import com.aihub.shared.idempotency.IdempotencySupport;
import com.aihub.taxonomy.api.TaxonomyApiContext;
import com.aihub.taxonomy.dictionary.application.DictionaryApplicationService;
import com.aihub.taxonomy.dictionary.application.DictionaryDtos.CreateDictionaryItemCommand;
import com.aihub.taxonomy.dictionary.application.DictionaryDtos.DictionaryItemView;
import com.aihub.taxonomy.dictionary.application.DictionaryDtos.DictionaryTypeView;
import com.aihub.taxonomy.dictionary.application.DictionaryDtos.UpdateDictionaryItemCommand;
import com.aihub.taxonomy.dictionary.api.DictionaryRequests.CreateDictionaryItemRequest;
import com.aihub.taxonomy.dictionary.api.DictionaryRequests.UpdateDictionaryItemRequest;
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
 * 字典管理 REST 适配器。
 *
 * <p>授权判定走统一 {@link AuthorizationService}（fail-closed）：读列表需 {@code dictionary:read}，
 * 新建/更新需 {@code dictionary:manage}。字典项列表支持 {@code includeDisabled} 参数控制是否回显停用项。
 */
@RestController
@RequestMapping("/api/v1/system/dictionaries")
public class DictionaryController {

    private final DictionaryApplicationService dictionaryService;
    private final AuthorizationService authorizationService;
    private final IdempotencyService idempotencyService;
    private final IdempotencySupport idempotency;

    public DictionaryController(DictionaryApplicationService dictionaryService,
                                AuthorizationService authorizationService,
                                IdempotencyService idempotencyService,
                                ObjectMapper objectMapper) {
        this.dictionaryService = dictionaryService;
        this.authorizationService = authorizationService;
        this.idempotencyService = idempotencyService;
        this.idempotency = new IdempotencySupport(objectMapper);
    }

    /**
     * 查询全部字典类型。
     */
    @GetMapping
    public ApiResponse<List<DictionaryTypeView>> listDictionaries() {
        authorizationService.requirePermission(Permissions.DICTIONARY_READ);
        return TaxonomyApiContext.respond(dictionaryService.listTypes());
    }

    /**
     * 查询字典项。
     *
     * @param includeDisabled 是否含停用项（默认 false 仅可引用项；true 含停用项用于历史回显）
     */
    @GetMapping("/{dictCode}/items")
    public ApiResponse<List<DictionaryItemView>> listItems(@PathVariable String dictCode,
                                                           @RequestParam(value = "includeDisabled",
                                                                   defaultValue = "false")
                                                           boolean includeDisabled) {
        authorizationService.requirePermission(Permissions.DICTIONARY_READ);
        return TaxonomyApiContext.respond(dictionaryService.listItems(dictCode, includeDisabled));
    }

    /**
     * 新增字典项。
     */
    @PostMapping("/{dictCode}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DictionaryItemView> createItem(
            @PathVariable String dictCode,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyValue,
            @RequestBody CreateDictionaryItemRequest request) {
        authorizationService.requirePermission(Permissions.DICTIONARY_MANAGE);
        if (request == null) {
            throw new ValidationException("request body is required");
        }
        IdempotencyKey key = idempotency.buildKey(idempotencyKeyValue,
                TaxonomyApiContext.principalId(), "POST",
                "/api/v1/system/dictionaries/" + dictCode + "/items");
        String fingerprint = idempotency.sha256Digest(request);
        AtomicReference<DictionaryItemView> ref = new AtomicReference<>();
        idempotencyService.execute(key, fingerprint, () -> {
            DictionaryItemView view = dictionaryService.createItem(dictCode,
                    new CreateDictionaryItemCommand(request.itemCode(), request.i18nKey(),
                            request.sortOrder()),
                    TaxonomyApiContext.principalId());
            ref.set(view);
            return new IdempotencyService.IdempotencyResponse(201, idempotency.serialize(view));
        });
        return TaxonomyApiContext.respond(ref.get());
    }

    /**
     * 更新、启用或停用字典项。
     */
    @PatchMapping("/{dictCode}/items/{itemCode}")
    public ApiResponse<DictionaryItemView> updateItem(
            @PathVariable String dictCode,
            @PathVariable String itemCode,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyValue,
            @RequestBody UpdateDictionaryItemRequest request) {
        authorizationService.requirePermission(Permissions.DICTIONARY_MANAGE);
        if (request == null) {
            throw new ValidationException("request body is required");
        }
        IdempotencyKey key = idempotency.buildKey(idempotencyKeyValue,
                TaxonomyApiContext.principalId(), "PATCH",
                "/api/v1/system/dictionaries/" + dictCode + "/items/" + itemCode);
        String fingerprint = idempotency.sha256Digest(request);
        AtomicReference<DictionaryItemView> ref = new AtomicReference<>();
        idempotencyService.execute(key, fingerprint, () -> {
            DictionaryItemView view = dictionaryService.updateItem(dictCode, itemCode,
                    new UpdateDictionaryItemCommand(request.i18nKey(), request.sortOrder(),
                            request.status(), request.expectedVersion()),
                    TaxonomyApiContext.principalId());
            ref.set(view);
            return new IdempotencyService.IdempotencyResponse(200, idempotency.serialize(view));
        });
        return TaxonomyApiContext.respond(ref.get());
    }
}
