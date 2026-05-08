package com.harnessdg.relation.controller;

import com.harnessdg.common.response.R;
import com.harnessdg.model.relation.dto.*;
import com.harnessdg.relation.service.RelationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 功能：实体关系管理 Controller
 * 时间：2026-05-08
 * 作者：AxeXie
 */
@RestController
@RequestMapping("/api/v1/ontology/relations")
@RequiredArgsConstructor
public class RelationController {

    private final RelationService relationService;

    /**
     * 查询所有关系，支持按源实体或目标实体过滤
     */
    @GetMapping
    public R<List<RelationDTO>> listRelations(
            @RequestParam(required = false) Long sourceEntityId,
            @RequestParam(required = false) Long targetEntityId,
            @RequestParam(required = false) String relationType,
            @RequestHeader(value = "Accept-Language", required = false) String locale) {
        return R.ok(relationService.listRelations(sourceEntityId, targetEntityId, relationType, locale));
    }

    /**
     * 查询某实体的所有关联关系
     */
    @GetMapping("/entity/{entityId}")
    public R<List<RelationDTO>> getRelationsByEntity(
            @PathVariable Long entityId,
            @RequestParam(required = false) String direction,
            @RequestHeader(value = "Accept-Language", required = false) String locale) {
        return R.ok(relationService.getRelationsByEntity(entityId, direction, locale));
    }

    /**
     * 创建关系
     */
    @PostMapping
    public R<RelationDTO> createRelation(@Valid @RequestBody RelationCreateRequest request) {
        return R.ok(relationService.createRelation(request));
    }

    /**
     * 更新关系
     */
    @PutMapping("/{id}")
    public R<RelationDTO> updateRelation(
            @PathVariable Long id,
            @Valid @RequestBody RelationUpdateRequest request) {
        return R.ok(relationService.updateRelation(id, request));
    }

    /**
     * 删除关系
     */
    @DeleteMapping("/{id}")
    public R<Void> deleteRelation(@PathVariable Long id) {
        relationService.deleteRelation(id);
        return R.ok(null);
    }
}
