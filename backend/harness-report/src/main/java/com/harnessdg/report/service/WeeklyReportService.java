/**
 * 功能：周报 Service 接口
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.report.service;

import com.harnessdg.model.report.dto.WeeklyReportCreateRequest;
import com.harnessdg.model.report.dto.WeeklyReportDTO;

import java.util.List;

public interface WeeklyReportService {

    List<WeeklyReportDTO> listReports(String reportType, String status);

    WeeklyReportDTO getReport(Long id);

    WeeklyReportDTO createReport(WeeklyReportCreateRequest request);

    WeeklyReportDTO updateReport(Long id, WeeklyReportCreateRequest request);

    void deleteReport(Long id);
}
