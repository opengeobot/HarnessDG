/**
 * 功能：质量检查记录 Mapper
 * 时间：2026-05-12
 * 作者：AxeXie
 */
package com.harnessdg.quality.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harnessdg.model.quality.entity.QualityCheck;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QualityCheckMapper extends BaseMapper<QualityCheck> {
}
