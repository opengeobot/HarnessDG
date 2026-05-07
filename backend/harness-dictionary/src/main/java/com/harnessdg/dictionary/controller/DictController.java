package com.harnessdg.dictionary.controller;

import com.harnessdg.common.response.R;
import com.harnessdg.dictionary.service.DictService;
import com.harnessdg.model.dict.dto.*;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dict")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    @GetMapping("/groups")
    public R<List<DictGroupDTO>> listGroups(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status) {
        return R.ok(dictService.listGroups(category, status));
    }

    @GetMapping("/groups/{code}")
    public R<DictGroupDTO> getGroup(
            @PathVariable String code,
            @RequestHeader(value = "Accept-Language", required = false) String locale) {
        return R.ok(dictService.getGroupByCode(code, locale));
    }

    @PostMapping("/groups")
    public R<DictGroupDTO> createGroup(@Valid @RequestBody DictGroupCreateRequest request) {
        return R.ok(dictService.createGroup(request));
    }

    @PutMapping("/groups/{code}")
    public R<DictGroupDTO> updateGroup(
            @PathVariable String code,
            @Valid @RequestBody DictGroupUpdateRequest request) {
        return R.ok(dictService.updateGroup(code, request));
    }

    @DeleteMapping("/groups/{code}")
    public R<Void> deleteGroup(@PathVariable String code) {
        dictService.deleteGroup(code);
        return R.ok(null);
    }

    @GetMapping("/items/{groupCode}")
    public R<List<DictItemDTO>> listItems(
            @PathVariable String groupCode,
            @RequestHeader(value = "Accept-Language", required = false) String locale) {
        return R.ok(dictService.listItems(groupCode, locale));
    }

    @GetMapping("/items/{groupCode}/tree")
    public R<List<DictItemDTO>> getItemTree(
            @PathVariable String groupCode,
            @RequestHeader(value = "Accept-Language", required = false) String locale) {
        return R.ok(dictService.getItemTree(groupCode, locale));
    }

    @PostMapping("/items")
    public R<DictItemDTO> createItem(@Valid @RequestBody DictItemCreateRequest request) {
        return R.ok(dictService.createItem(request));
    }

    @PutMapping("/items/{id}")
    public R<DictItemDTO> updateItem(
            @PathVariable Long id,
            @Valid @RequestBody DictItemUpdateRequest request) {
        return R.ok(dictService.updateItem(id, request));
    }

    @DeleteMapping("/items/{id}")
    public R<Void> deleteItem(@PathVariable Long id) {
        dictService.deleteItem(id);
        return R.ok(null);
    }

    @PostMapping("/items/{groupCode}/batch")
    public R<List<DictItemDTO>> batchCreateItems(
            @PathVariable String groupCode,
            @Valid @RequestBody List<DictItemCreateRequest> requests) {
        return R.ok(dictService.batchCreateItems(groupCode, requests));
    }

    /**
     * 批量获取多个分组的字典项，供前端启动时预加载高频字典使用
     */
    @PostMapping("/items/batch")
    public R<Map<String, List<DictItemDTO>>> batchGetItems(
            @RequestBody BatchGetItemsRequest request,
            @RequestHeader(value = "Accept-Language", required = false) String locale) {
        return R.ok(dictService.batchGetItems(request.getGroupCodes(), locale));
    }

    /**
     * 全局搜索字典项，可限定到某个分组
     */
    @GetMapping("/search")
    public R<List<DictItemDTO>> searchItems(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String groupCode,
            @RequestHeader(value = "Accept-Language", required = false) String locale) {
        return R.ok(dictService.searchItems(keyword, groupCode, locale));
    }

    /**
     * 导出某分组的字典数据（JSON）
     */
    @GetMapping("/export")
    public R<DictExportData> exportGroup(@RequestParam String groupCode) {
        return R.ok(dictService.exportGroup(groupCode));
    }

    /**
     * 导入字典数据（JSON）
     */
    @PostMapping("/import")
    public R<DictGroupDTO> importGroup(
            @RequestBody DictExportData data,
            @RequestParam(defaultValue = "false") boolean overwrite) {
        return R.ok(dictService.importGroup(data, overwrite));
    }

    @Data
    public static class BatchGetItemsRequest {
        private List<String> groupCodes;
    }
}
