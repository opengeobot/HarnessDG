-- V016__seed_phase2_dict_data.sql
-- 功能：Phase 2 字典初始化数据（审批/质量/血缘/数据源/报告/异常状态等）
-- 时间：2026-05-08
-- 作者：AxeXie
-- 注意：Phase 2 字典数据通过应用初始化代码插入，而非 Flyway 脚本

-- 空迁移 - 标记版本
SELECT 'V016 Phase 2 dict seed skipped - will be inserted via application init' AS status;
