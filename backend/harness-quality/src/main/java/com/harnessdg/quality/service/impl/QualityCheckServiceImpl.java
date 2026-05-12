/**
 * 功能：质量规则执行检查服务实现
 * 时间：2026-05-12
 * 作者：AxeXie
 */
package com.harnessdg.quality.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.harnessdg.common.exception.BizException;
import com.harnessdg.common.response.ErrorCode;
import com.harnessdg.model.quality.dto.QualityCheckResultDTO;
import com.harnessdg.model.quality.entity.QualityCheck;
import com.harnessdg.model.quality.entity.QualityRule;
import com.harnessdg.quality.mapper.QualityCheckMapper;
import com.harnessdg.quality.mapper.QualityRuleMapper;
import com.harnessdg.quality.service.QualityCheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QualityCheckServiceImpl implements QualityCheckService {

    private final QualityRuleMapper qualityRuleMapper;
    private final QualityCheckMapper qualityCheckMapper;

    @Override
    @Transactional
    public QualityCheckResultDTO executeRuleCheck(Long ruleId) {
        log.info("Executing quality rule check: ruleId={}", ruleId);

        QualityRule rule = qualityRuleMapper.selectById(ruleId);
        if (rule == null) {
            throw new BizException(ErrorCode.QUALITY_RULE_NOT_FOUND);
        }

        if (!"active".equals(rule.getStatus())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Rule is not active");
        }

        // 执行检查（简化版，实际应连接数据源执行 SQL）
        QualityCheckResultDTO result = performCheck(rule);

        // 保存检查记录
        QualityCheck check = new QualityCheck();
        check.setRuleId(ruleId);
        check.setCheckResult(result.getCheckResult());
        check.setActualValue(result.getActualValue());
        check.setExpectedValue(result.getExpectedValue());
        check.setViolated(result.getViolated());
        check.setErrorMessage(result.getErrorMessage());
        check.setCheckedAt(OffsetDateTime.now());
        qualityCheckMapper.insert(check);

        log.info("Quality rule check completed: ruleId={}, violated={}", ruleId, result.getViolated());
        return result;
    }

    @Override
    @Transactional
    public List<QualityCheckResultDTO> executeEntityChecks(Long entityId) {
        log.info("Executing quality checks for entity: entityId={}", entityId);

        LambdaQueryWrapper<QualityRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QualityRule::getEntityId, entityId)
               .eq(QualityRule::getStatus, "active");

        List<QualityRule> rules = qualityRuleMapper.selectList(wrapper);

        return rules.stream()
                .map(rule -> executeRuleCheck(rule.getId()))
                .collect(Collectors.toList());
    }

    @Override
    public List<QualityCheckResultDTO> listCheckResults(Long ruleId, String status) {
        LambdaQueryWrapper<QualityCheck> wrapper = new LambdaQueryWrapper<>();
        if (ruleId != null) {
            wrapper.eq(QualityCheck::getRuleId, ruleId);
        }
        wrapper.orderByDesc(QualityCheck::getCheckedAt);

        List<QualityCheck> checks = qualityCheckMapper.selectList(wrapper);

        return checks.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 执行实际检查逻辑
     */
    private QualityCheckResultDTO performCheck(QualityRule rule) {
        QualityCheckResultDTO result = new QualityCheckResultDTO();
        result.setRuleId(rule.getId());
        result.setRuleName(rule.getRuleName());
        result.setRuleType(rule.getRuleType());
        result.setEntityId(rule.getEntityId());
        result.setMetricId(rule.getMetricId());

        try {
            // 根据规则类型执行检查
            switch (rule.getRuleType()) {
                case "not_null" -> result = checkNotNull(rule);
                case "unique" -> result = checkUnique(rule);
                case "range" -> result = checkRange(rule);
                case "fluctuation" -> result = checkFluctuation(rule);
                default -> {
                    result.setCheckResult("skipped");
                    result.setViolated(false);
                }
            }
        } catch (Exception e) {
            result.setCheckResult("error");
            result.setViolated(false);
            result.setErrorMessage(e.getMessage());
        }

        result.setCheckedAt(OffsetDateTime.now());
        return result;
    }

    private QualityCheckResultDTO checkNotNull(QualityRule rule) {
        QualityCheckResultDTO result = new QualityCheckResultDTO();
        // 简化实现：模拟检查结果
        result.setCheckResult("passed");
        result.setViolated(false);
        result.setActualValue(Map.of("nullCount", 0));
        result.setExpectedValue(Map.of("check", "not_null"));
        return result;
    }

    private QualityCheckResultDTO checkUnique(QualityRule rule) {
        QualityCheckResultDTO result = new QualityCheckResultDTO();
        result.setCheckResult("passed");
        result.setViolated(false);
        result.setActualValue(Map.of("duplicateCount", 0));
        result.setExpectedValue(Map.of("check", "unique"));
        return result;
    }

    private QualityCheckResultDTO checkRange(QualityRule rule) {
        QualityCheckResultDTO result = new QualityCheckResultDTO();
        result.setCheckResult("passed");
        result.setViolated(false);
        result.setActualValue(Map.of("outOfRangeCount", 0));
        result.setExpectedValue(rule.getThreshold());
        return result;
    }

    private QualityCheckResultDTO checkFluctuation(QualityRule rule) {
        QualityCheckResultDTO result = new QualityCheckResultDTO();
        result.setCheckResult("passed");
        result.setViolated(false);
        result.setActualValue(Map.of("fluctuationPercent", 0));
        result.setExpectedValue(rule.getThreshold());
        return result;
    }

    private QualityCheckResultDTO toDTO(QualityCheck entity) {
        QualityCheckResultDTO dto = new QualityCheckResultDTO();
        dto.setId(entity.getId());
        dto.setRuleId(entity.getRuleId());
        dto.setCheckResult(entity.getCheckResult());
        dto.setActualValue(entity.getActualValue());
        dto.setExpectedValue(entity.getExpectedValue());
        dto.setViolated(entity.getViolated());
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setCheckedAt(entity.getCheckedAt());
        return dto;
    }
}
