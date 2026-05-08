/**
 * 功能：审批模板 Mapper 接口
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.approval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harnessdg.model.approval.entity.ApprovalTemplate;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ApprovalTemplateMapper extends BaseMapper<ApprovalTemplate> {
}
