/**
 * 功能：审批步骤 Mapper 接口
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.approval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harnessdg.model.approval.entity.ApprovalStep;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ApprovalStepMapper extends BaseMapper<ApprovalStep> {
}
