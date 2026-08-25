package com.modelhub.api.controller;

import com.modelhub.api.support.Principals;
import com.modelhub.identity.security.CurrentPrincipal;
import com.modelhub.identity.service.SysDictService;
import com.modelhub.identity.service.SysDictService.DictItemView;
import com.modelhub.identity.service.SysDictService.DictView;
import com.modelhub.shared.web.ApiEnvelope;
import com.modelhub.shared.web.ETags;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 管理端字典端点（管理后台计划 §五）：字典与字典项 CRUD。
 * 仅 platform_admin（服务层二次校验）；写操作要求 Idempotency-Key 并回显。
 */
@RestController
@RequestMapping("/api/v1/admin/dicts")
public class AdminDictController {

    /** POST /dicts 请求体。 */
    public record CreateDictBody(String dictCode, String name, String description) {}

    /** PATCH /dicts/{dictId} 请求体：字段为 null 表示不修改。 */
    public record UpdateDictBody(String name, String description) {}

    /** POST /dicts/{dictId}/items 请求体。 */
    public record CreateItemBody(String itemValue, String labelZh, String labelEn, Integer sortOrder, String remark) {}

    /** PATCH /dicts/{dictId}/items/{itemId} 请求体：字段为 null 表示不修改（item_value 不可改）。 */
    public record UpdateItemBody(String labelZh, String labelEn, Integer sortOrder, String remark) {}

    private final SysDictService dictService;

    public AdminDictController(SysDictService dictService) {
        this.dictService = dictService;
    }

    @GetMapping
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> list(HttpServletRequest request) {
        CurrentPrincipal actor = Principals.requireCurrent(request);
        List<DictView> dicts = dictService.listDicts(actor);
        return ResponseEntity.ok(ApiEnvelope.ok(Map.of("items", dicts)));
    }

    @PostMapping
    public ResponseEntity<ApiEnvelope<DictView>> create(@RequestBody CreateDictBody body,
                                                        @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                        HttpServletRequest request) {
        CurrentPrincipal actor = Principals.requireCurrent(request);
        DictView dict = dictService.createDict(actor, body.dictCode(), body.name(), body.description());
        return ResponseEntity.status(HttpStatus.CREATED).header("Idempotency-Key", idempotencyKey)
                .body(ApiEnvelope.created(dict));
    }

    @GetMapping("/{dictId:[0-9a-fA-F-]{36}}")
    public ResponseEntity<ApiEnvelope<DictView>> get(@PathVariable("dictId") UUID dictId,
                                                     HttpServletRequest request) {
        CurrentPrincipal actor = Principals.requireCurrent(request);
        DictView dict = dictService.getDict(actor, dictId);
        return ResponseEntity.ok(ApiEnvelope.ok(dict));
    }

    @PatchMapping("/{dictId:[0-9a-fA-F-]{36}}")
    public ResponseEntity<ApiEnvelope<DictView>> update(@PathVariable("dictId") UUID dictId,
                                                        @RequestBody UpdateDictBody body,
                                                        @RequestHeader("If-Match") String ifMatch,
                                                        @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                        HttpServletRequest request) {
        CurrentPrincipal actor = Principals.requireCurrent(request);
        DictView dict = dictService.updateDict(actor, dictId, body.name(), body.description(), ifMatch);
        return ResponseEntity.ok().header("Idempotency-Key", idempotencyKey)
                .header("ETag", ETags.ofVersion(dict.version()))
                .body(ApiEnvelope.ok(dict));
    }

    @DeleteMapping("/{dictId:[0-9a-fA-F-]{36}}")
    public ResponseEntity<Void> delete(@PathVariable("dictId") UUID dictId,
                                       @RequestHeader("Idempotency-Key") String idempotencyKey,
                                       HttpServletRequest request) {
        CurrentPrincipal actor = Principals.requireCurrent(request);
        dictService.deleteDict(actor, dictId);
        return ResponseEntity.noContent().header("Idempotency-Key", idempotencyKey).build();
    }

    // ---------- 字典项 ----------

    @GetMapping("/{dictId:[0-9a-fA-F-]{36}}/items")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> listItems(@PathVariable("dictId") UUID dictId,
                                                                      HttpServletRequest request) {
        CurrentPrincipal actor = Principals.requireCurrent(request);
        List<DictItemView> items = dictService.listItems(actor, dictId);
        return ResponseEntity.ok(ApiEnvelope.ok(Map.of("items", items)));
    }

    @PostMapping("/{dictId:[0-9a-fA-F-]{36}}/items")
    public ResponseEntity<ApiEnvelope<DictItemView>> createItem(@PathVariable("dictId") UUID dictId,
                                                                @RequestBody CreateItemBody body,
                                                                @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                                HttpServletRequest request) {
        CurrentPrincipal actor = Principals.requireCurrent(request);
        DictItemView item = dictService.createItem(actor, dictId, body.itemValue(), body.labelZh(),
                body.labelEn(), body.sortOrder(), body.remark());
        return ResponseEntity.status(HttpStatus.CREATED).header("Idempotency-Key", idempotencyKey)
                .body(ApiEnvelope.created(item));
    }

    @PatchMapping("/{dictId:[0-9a-fA-F-]{36}}/items/{itemId}")
    public ResponseEntity<ApiEnvelope<DictItemView>> updateItem(@PathVariable("dictId") UUID dictId,
                                                                @PathVariable("itemId") long itemId,
                                                                @RequestBody UpdateItemBody body,
                                                                @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                                HttpServletRequest request) {
        CurrentPrincipal actor = Principals.requireCurrent(request);
        DictItemView item = dictService.updateItem(actor, dictId, itemId, body.labelZh(), body.labelEn(),
                body.sortOrder(), body.remark());
        return ResponseEntity.ok().header("Idempotency-Key", idempotencyKey)
                .body(ApiEnvelope.ok(item));
    }

    @DeleteMapping("/{dictId:[0-9a-fA-F-]{36}}/items/{itemId}")
    public ResponseEntity<Void> deleteItem(@PathVariable("dictId") UUID dictId,
                                           @PathVariable("itemId") long itemId,
                                           @RequestHeader("Idempotency-Key") String idempotencyKey,
                                           HttpServletRequest request) {
        CurrentPrincipal actor = Principals.requireCurrent(request);
        dictService.deleteItem(actor, dictId, itemId);
        return ResponseEntity.noContent().header("Idempotency-Key", idempotencyKey).build();
    }

    @PostMapping("/{dictId:[0-9a-fA-F-]{36}}/items/{itemId}:disable")
    public ResponseEntity<ApiEnvelope<DictItemView>> disableItem(@PathVariable("dictId") UUID dictId,
                                                                 @PathVariable("itemId") long itemId,
                                                                 @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                                 HttpServletRequest request) {
        CurrentPrincipal actor = Principals.requireCurrent(request);
        DictItemView item = dictService.setItemStatus(actor, dictId, itemId, false);
        return ResponseEntity.ok().header("Idempotency-Key", idempotencyKey)
                .body(ApiEnvelope.ok(item));
    }

    @PostMapping("/{dictId:[0-9a-fA-F-]{36}}/items/{itemId}:enable")
    public ResponseEntity<ApiEnvelope<DictItemView>> enableItem(@PathVariable("dictId") UUID dictId,
                                                                @PathVariable("itemId") long itemId,
                                                                @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                                HttpServletRequest request) {
        CurrentPrincipal actor = Principals.requireCurrent(request);
        DictItemView item = dictService.setItemStatus(actor, dictId, itemId, true);
        return ResponseEntity.ok().header("Idempotency-Key", idempotencyKey)
                .body(ApiEnvelope.ok(item));
    }
}
