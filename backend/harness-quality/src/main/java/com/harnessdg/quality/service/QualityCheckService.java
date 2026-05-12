/**
 * 功能：质量规则执行检查服务
 * 时间：2026-05-12
 * 作者：AxeXie
 */
package com.harnessdg.quality.service;

import com.harnessdg.model.quality.dto.QualityCheckResultDTO;

import java.util.List;

public interface QualityCheckService {

    /**
     * 执行单个质量规则检查
     *
     * @param ruleId 质量规则 ID
     * @return 检查结果
     */
    QualityCheckResultDTO executeRuleCheck(Long ruleId);

    /**
     * 批量执行某实体的所有质量规则
     *
     * @param entityId 实体 ID
     * @return 检查结果列表
     */
    List<QualityCheckResultDTO> executeEntityChecks(Long entityId);

    /**
     * 获取检查结果列表
     *
     * @param ruleId 规则 ID（可选）
     * @param status 状态（可选）
     * @return 检查结果列表
     */
    List<QualityCheckResultDTO> listCheckResults(Long ruleId, String status);
}
