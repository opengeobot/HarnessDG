/**
 * 功能：周报 Mapper
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harnessdg.model.report.entity.WeeklyReport;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WeeklyReportMapper extends BaseMapper<WeeklyReport> {
}
