/**
 * 功能：周报 Controller
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.report.controller;

import com.harnessdg.common.response.R;
import com.harnessdg.model.report.dto.WeeklyReportCreateRequest;
import com.harnessdg.model.report.dto.WeeklyReportDTO;
import com.harnessdg.report.service.WeeklyReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class WeeklyReportController {

    private final WeeklyReportService weeklyReportService;

    @GetMapping
    public R<List<WeeklyReportDTO>> listReports(
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) String status) {
        return R.ok(weeklyReportService.listReports(reportType, status));
    }

    @GetMapping("/{id}")
    public R<WeeklyReportDTO> getReport(@PathVariable Long id) {
        return R.ok(weeklyReportService.getReport(id));
    }

    @PostMapping
    public R<WeeklyReportDTO> createReport(@Valid @RequestBody WeeklyReportCreateRequest request) {
        return R.ok(weeklyReportService.createReport(request));
    }

    @PutMapping("/{id}")
    public R<WeeklyReportDTO> updateReport(
            @PathVariable Long id,
            @Valid @RequestBody WeeklyReportCreateRequest request) {
        return R.ok(weeklyReportService.updateReport(id, request));
    }

    @DeleteMapping("/{id}")
    public R<Void> deleteReport(@PathVariable Long id) {
        weeklyReportService.deleteReport(id);
        return R.ok(null);
    }
}
