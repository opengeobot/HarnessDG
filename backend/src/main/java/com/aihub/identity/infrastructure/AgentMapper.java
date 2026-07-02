/*
 * 功能: Agent 单表 Mapper（MyBatis-Plus BaseMapper）。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 单表 Mapper（{@code iam_agent} 表）。
 */
@Mapper
public interface AgentMapper extends BaseMapper<AgentEntity> {
}
