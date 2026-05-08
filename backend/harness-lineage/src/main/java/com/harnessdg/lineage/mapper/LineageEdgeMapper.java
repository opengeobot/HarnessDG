/**
 * 功能：血缘边 Mapper
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.lineage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harnessdg.model.lineage.entity.LineageEdge;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LineageEdgeMapper extends BaseMapper<LineageEdge> {
}
