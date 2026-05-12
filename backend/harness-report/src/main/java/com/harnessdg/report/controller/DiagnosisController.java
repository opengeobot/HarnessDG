/**
 * 功能：异常诊断控制器
 * 时间：2026-05-12
 * 作者：AxeXie
 */
package com.harnessdg.report.controller;

import com.harnessdg.common.response.R;
import com.harnessdg.model.report.dto.DiagnosisResultDTO;
import com.harnessdg.report.service.DiagnosisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/diagnosis")
@RequiredArgsConstructor
public class DiagnosisController {

    private final DiagnosisService diagnosisService;

    /**
     * 对失败的任务进行诊断
     */
    @PostMapping
    public R<DiagnosisResultDTO> diagnose(
            @RequestParam Long taskId,
            @RequestParam String taskType,
            @RequestParam String errorMessage) {
        try {
            DiagnosisResultDTO result = diagnosisService.diagnose(taskId, taskType, errorMessage);
            return R.ok(result);
        } catch (Exception e) {
            log.error("Failed to diagnose task: taskId={}", taskId, e);
            return R.fail(com.harnessdg.common.response.ErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }
}
