/**
 * 功能：质量规则 Controller
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.quality.controller;

import com.harnessdg.common.response.R;
import com.harnessdg.model.quality.dto.QualityRuleAutoGenerateRequest;
import com.harnessdg.model.quality.dto.QualityRuleCreateRequest;
import com.harnessdg.model.quality.dto.QualityRuleDTO;
import com.harnessdg.quality.service.QualityRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/quality/rules")
@RequiredArgsConstructor
public class QualityRuleController {

    private final QualityRuleService qualityRuleService;

    @GetMapping
    public R<List<QualityRuleDTO>> listRules(
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) String ruleType,
            @RequestParam(required = false) String status) {
        return R.ok(qualityRuleService.listRules(entityId, ruleType, status));
    }

    @GetMapping("/{id}")
    public R<QualityRuleDTO> getRule(@PathVariable Long id) {
        return R.ok(qualityRuleService.getRule(id));
    }

    @PostMapping
    public R<QualityRuleDTO> createRule(@Valid @RequestBody QualityRuleCreateRequest request) {
        return R.ok(qualityRuleService.createRule(request));
    }

    @PutMapping("/{id}")
    public R<QualityRuleDTO> updateRule(
            @PathVariable Long id,
            @Valid @RequestBody QualityRuleCreateRequest request) {
        return R.ok(qualityRuleService.updateRule(id, request));
    }

    @DeleteMapping("/{id}")
    public R<Void> deleteRule(@PathVariable Long id) {
        qualityRuleService.deleteRule(id);
        return R.ok(null);
    }

    @PostMapping("/auto-generate")
    public R<List<QualityRuleDTO>> autoGenerateRules(@Valid @RequestBody QualityRuleAutoGenerateRequest request) {
        return R.ok(qualityRuleService.autoGenerateRules(request));
    }
}
