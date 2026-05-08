/**
 * 功能：质量规则 Mapper
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.quality.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harnessdg.model.quality.entity.QualityRule;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QualityRuleMapper extends BaseMapper<QualityRule> {
}
