/**
 * 功能：系统配置变更历史 Mapper
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.config.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harnessdg.model.config.entity.SysConfigHistory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysConfigHistoryMapper extends BaseMapper<SysConfigHistory> {
}
