/**
 * 功能：数据接入任务 Mapper
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.datasource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harnessdg.model.datasource.entity.IngestionTask;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IngestionTaskMapper extends BaseMapper<IngestionTask> {
}
