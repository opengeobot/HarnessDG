/**
 * 功能：质量检查执行控制器
 * 时间：2026-05-12
 * 作者：AxeXie
 */
package com.harnessdg.quality.controller;

import com.harnessdg.common.response.R;
import com.harnessdg.model.quality.dto.QualityCheckResultDTO;
import com.harnessdg.quality.service.QualityCheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/quality/checks")
@RequiredArgsConstructor
public class QualityCheckController {

    private final QualityCheckService qualityCheckService;

    /**
     * 执行单个质量规则检查
     */
    @PostMapping("/rule/{ruleId}")
    public R<QualityCheckResultDTO> executeRuleCheck(@PathVariable Long ruleId) {
        try {
            QualityCheckResultDTO result = qualityCheckService.executeRuleCheck(ruleId);
            return R.ok(result);
        } catch (Exception e) {
            log.error("Failed to execute rule check: ruleId={}", ruleId, e);
            return R.fail(com.harnessdg.common.response.ErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    /**
     * 批量执行某实体的所有质量规则检查
     */
    @PostMapping("/entity/{entityId}")
    public R<List<QualityCheckResultDTO>> executeEntityChecks(@PathVariable Long entityId) {
        try {
            List<QualityCheckResultDTO> results = qualityCheckService.executeEntityChecks(entityId);
            return R.ok(results);
        } catch (Exception e) {
            log.error("Failed to execute entity checks: entityId={}", entityId, e);
            return R.fail(com.harnessdg.common.response.ErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    /**
     * 获取检查结果列表
     */
    @GetMapping
    public R<List<QualityCheckResultDTO>> listCheckResults(
            @RequestParam(required = false) Long ruleId,
            @RequestParam(required = false) String status) {
        try {
            List<QualityCheckResultDTO> results = qualityCheckService.listCheckResults(ruleId, status);
            return R.ok(results);
        } catch (Exception e) {
            log.error("Failed to list check results", e);
            return R.fail(com.harnessdg.common.response.ErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }
}
