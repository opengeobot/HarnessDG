/**
 * 功能：质量规则 Service 接口
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.quality.service;

import com.harnessdg.model.quality.dto.QualityRuleAutoGenerateRequest;
import com.harnessdg.model.quality.dto.QualityRuleCreateRequest;
import com.harnessdg.model.quality.dto.QualityRuleDTO;

import java.util.List;

public interface QualityRuleService {

    List<QualityRuleDTO> listRules(Long entityId, String ruleType, String status);

    QualityRuleDTO getRule(Long id);

    QualityRuleDTO createRule(QualityRuleCreateRequest request);

    QualityRuleDTO updateRule(Long id, QualityRuleCreateRequest request);

    void deleteRule(Long id);

    List<QualityRuleDTO> autoGenerateRules(QualityRuleAutoGenerateRequest request);
}
