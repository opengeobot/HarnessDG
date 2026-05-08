/**
 * 功能：审批实例 Mapper 接口
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.approval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harnessdg.model.approval.entity.ApprovalInstance;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ApprovalInstanceMapper extends BaseMapper<ApprovalInstance> {
}
