/**
 * 功能：血缘 Controller
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.lineage.controller;

import com.harnessdg.common.response.R;
import com.harnessdg.lineage.service.LineageService;
import com.harnessdg.model.lineage.dto.LineageCreateRequest;
import com.harnessdg.model.lineage.dto.LineageEdgeDTO;
import com.harnessdg.model.lineage.dto.LineageNodeDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/lineage")
@RequiredArgsConstructor
public class LineageController {

    private final LineageService lineageService;

    @GetMapping("/nodes")
    public R<List<LineageNodeDTO>> listNodes(
            @RequestParam(required = false) String nodeType,
            @RequestParam(required = false) Long entityId) {
        return R.ok(lineageService.listNodes(nodeType, entityId));
    }

    @GetMapping("/nodes/{id}")
    public R<LineageNodeDTO> getNode(@PathVariable Long id) {
        return R.ok(lineageService.getNode(id));
    }

    @GetMapping("/edges/{nodeId}")
    public R<List<LineageEdgeDTO>> listEdges(@PathVariable Long nodeId) {
        return R.ok(lineageService.listEdges(nodeId));
    }

    @PostMapping
    public R<Void> createLineage(@Valid @RequestBody LineageCreateRequest request) {
        lineageService.createLineage(request);
        return R.ok(null);
    }

    @DeleteMapping("/nodes/{id}")
    public R<Void> deleteLineage(@PathVariable Long id) {
        lineageService.deleteLineage(id);
        return R.ok(null);
    }
}
