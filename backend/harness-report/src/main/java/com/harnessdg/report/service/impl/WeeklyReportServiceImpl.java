/**
 * 功能：周报 Service 实现
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.harnessdg.common.exception.BizException;
import com.harnessdg.common.response.ErrorCode;
import com.harnessdg.model.report.dto.WeeklyReportCreateRequest;
import com.harnessdg.model.report.dto.WeeklyReportDTO;
import com.harnessdg.model.report.entity.WeeklyReport;
import com.harnessdg.report.mapper.WeeklyReportMapper;
import com.harnessdg.report.service.WeeklyReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WeeklyReportServiceImpl implements WeeklyReportService {

    private final WeeklyReportMapper weeklyReportMapper;

    @Override
    public List<WeeklyReportDTO> listReports(String reportType, String status) {
        LambdaQueryWrapper<WeeklyReport> wrapper = new LambdaQueryWrapper<>();
        if (reportType != null && !reportType.isBlank()) {
            wrapper.eq(WeeklyReport::getReportType, reportType);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(WeeklyReport::getStatus, status);
        }
        wrapper.orderByDesc(WeeklyReport::getCreatedAt);
        return weeklyReportMapper.selectList(wrapper).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public WeeklyReportDTO getReport(Long id) {
        WeeklyReport entity = weeklyReportMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.REPORT_NOT_FOUND);
        }
        return toDTO(entity);
    }

    @Override
    @Transactional
    public WeeklyReportDTO createReport(WeeklyReportCreateRequest request) {
        WeeklyReport entity = new WeeklyReport();
        entity.setReportType(request.getReportType());
        entity.setTitle(request.getTitle());
        entity.setTimeRangeStart(request.getTimeRangeStart());
        entity.setTimeRangeEnd(request.getTimeRangeEnd());
        entity.setContentJson(request.getContentJson());
        entity.setMarkdownContent(request.getMarkdownContent());
        entity.setGeneratedBy(request.getGeneratedBy());
        entity.setAgentSessionId(request.getAgentSessionId());
        entity.setStatus("draft");
        weeklyReportMapper.insert(entity);
        return toDTO(entity);
    }

    @Override
    @Transactional
    public WeeklyReportDTO updateReport(Long id, WeeklyReportCreateRequest request) {
        WeeklyReport entity = weeklyReportMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.REPORT_NOT_FOUND);
        }

        if (request.getReportType() != null) entity.setReportType(request.getReportType());
        if (request.getTitle() != null) entity.setTitle(request.getTitle());
        if (request.getTimeRangeStart() != null) entity.setTimeRangeStart(request.getTimeRangeStart());
        if (request.getTimeRangeEnd() != null) entity.setTimeRangeEnd(request.getTimeRangeEnd());
        if (request.getContentJson() != null) entity.setContentJson(request.getContentJson());
        if (request.getMarkdownContent() != null) entity.setMarkdownContent(request.getMarkdownContent());
        if (request.getGeneratedBy() != null) entity.setGeneratedBy(request.getGeneratedBy());
        if (request.getAgentSessionId() != null) entity.setAgentSessionId(request.getAgentSessionId());

        weeklyReportMapper.updateById(entity);
        return toDTO(entity);
    }

    @Override
    @Transactional
    public void deleteReport(Long id) {
        WeeklyReport entity = weeklyReportMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.REPORT_NOT_FOUND);
        }
        weeklyReportMapper.deleteById(id);
    }

    private WeeklyReportDTO toDTO(WeeklyReport entity) {
        WeeklyReportDTO dto = new WeeklyReportDTO();
        dto.setId(entity.getId());
        dto.setReportType(entity.getReportType());
        dto.setTitle(entity.getTitle());
        dto.setTimeRangeStart(entity.getTimeRangeStart());
        dto.setTimeRangeEnd(entity.getTimeRangeEnd());
        dto.setContentJson(entity.getContentJson());
        dto.setMarkdownContent(entity.getMarkdownContent());
        dto.setMetricsSnapshot(entity.getMetricsSnapshot());
        dto.setGeneratedBy(entity.getGeneratedBy());
        dto.setAgentSessionId(entity.getAgentSessionId());
        dto.setStatus(entity.getStatus());
        dto.setErrorMessage(entity.getErrorMessage());
        return dto;
    }
}
