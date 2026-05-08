-- V016__seed_phase2_dict_data.sql
-- 功能：Phase 2 字典初始化数据（审批/质量/血缘/数据源/报告/异常状态等）
-- 时间：2026-05-08
-- 作者：AxeXie

-- ==========================================
-- 1. 审批状态字典 (approval_status)
-- ==========================================
INSERT INTO sys_dict_group (code, name, description, category, is_tree, is_multiple, is_editable, status) VALUES
('approval_status', '{"zh_CN": "审批状态", "en_US": "Approval Status"}', '{"zh_CN": "审批流程状态", "en_US": "Approval workflow status"}', 'business', FALSE, FALSE, FALSE, 'active')
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_group WHERE code = 'approval_status');

INSERT INTO sys_dict_item (group_code, code, label, description, value, color, sort_order, is_system, status) VALUES
('approval_status', 'draft', '{"zh_CN": "草稿", "en_US": "Draft"}', '{"zh_CN": "审批草稿", "en_US": "Approval draft"}', 'draft', '#8C8C8C', 1, TRUE, 'active'),
('approval_status', 'pending', '{"zh_CN": "待审批", "en_US": "Pending"}', '{"zh_CN": "等待审批", "en_US": "Waiting for approval"}', 'pending', '#FA8C16', 2, TRUE, 'active'),
('approval_status', 'approved', '{"zh_CN": "已通过", "en_US": "Approved"}', '{"zh_CN": "审批通过", "en_US": "Approval passed"}', 'approved', '#52C41A', 3, TRUE, 'active'),
('approval_status', 'rejected', '{"zh_CN": "已驳回", "en_US": "Rejected"}', '{"zh_CN": "审批驳回", "en_US": "Approval rejected"}', 'rejected', '#FF4D4F', 4, TRUE, 'active'),
('approval_status', 'cancelled', '{"zh_CN": "已取消", "en_US": "Cancelled"}', '{"zh_CN": "审批取消", "en_US": "Approval cancelled"}', 'cancelled', '#BFBFBF', 5, TRUE, 'active')
ON CONFLICT (group_code, code) DO NOTHING;

-- ==========================================
-- 2. 质量规则类型字典 (quality_rule_type)
-- ==========================================
INSERT INTO sys_dict_group (code, name, description, category, is_tree, is_multiple, is_editable, status) VALUES
('quality_rule_type', '{"zh_CN": "质量规则类型", "en_US": "Quality Rule Type"}', '{"zh_CN": "数据质量规则分类", "en_US": "Data quality rule categories"}', 'business', FALSE, FALSE, FALSE, 'active')
ON CONFLICT (code) DO NOTHING;

INSERT INTO sys_dict_item (group_code, code, label, description, value, sort_order, is_system, status) VALUES
('quality_rule_type', 'not_null', '{"zh_CN": "非空检查", "en_US": "Not Null Check"}', '{"zh_CN": "检查字段不为空", "en_US": "Check field is not null"}', 'not_null', 1, TRUE, 'active'),
('quality_rule_type', 'unique', '{"zh_CN": "唯一性检查", "en_US": "Uniqueness Check"}', '{"zh_CN": "检查字段值唯一", "en_US": "Check field value uniqueness"}', 'unique', 2, TRUE, 'active'),
('quality_rule_type', 'range', '{"zh_CN": "范围检查", "en_US": "Range Check"}', '{"zh_CN": "检查数值范围", "en_US": "Check numeric range"}', 'range', 3, TRUE, 'active'),
('quality_rule_type', 'enum', '{"zh_CN": "枚举值检查", "en_US": "Enum Check"}', '{"zh_CN": "检查枚举值合法性", "en_US": "Check enum value validity"}', 'enum', 4, TRUE, 'active'),
('quality_rule_type', 'regex', '{"zh_CN": "正则检查", "en_US": "Regex Check"}', '{"zh_CN": "检查正则匹配", "en_US": "Check regex pattern"}', 'regex', 5, TRUE, 'active'),
('quality_rule_type', 'fluctuation', '{"zh_CN": "波动检查", "en_US": "Fluctuation Check"}', '{"zh_CN": "检查数据波动", "en_US": "Check data fluctuation"}', 'fluctuation', 6, TRUE, 'active'),
('quality_rule_type', 'custom_sql', '{"zh_CN": "自定义 SQL", "en_US": "Custom SQL"}', '{"zh_CN": "自定义 SQL 检查", "en_US": "Custom SQL check"}', 'custom_sql', 7, TRUE, 'active')
ON CONFLICT (group_code, code) DO NOTHING;

-- ==========================================
-- 3. 血缘节点类型字典 (lineage_node_type)
-- ==========================================
INSERT INTO sys_dict_group (code, name, description, category, is_tree, is_multiple, is_editable, status) VALUES
('lineage_node_type', '{"zh_CN": "血缘节点类型", "en_US": "Lineage Node Type"}', '{"zh_CN": "血缘图节点类型", "en_US": "Lineage graph node types"}', 'business', FALSE, FALSE, FALSE, 'active')
ON CONFLICT (code) DO NOTHING;

INSERT INTO sys_dict_item (group_code, code, label, description, value, sort_order, is_system, status) VALUES
('lineage_node_type', 'data_source', '{"zh_CN": "数据源", "en_US": "Data Source"}', '{"zh_CN": "外部数据源", "en_US": "External data source"}', 'data_source', 1, TRUE, 'active'),
('lineage_node_type', 'table', '{"zh_CN": "数据表", "en_US": "Table"}', '{"zh_CN": "物理表", "en_US": "Physical table"}', 'table', 2, TRUE, 'active'),
('lineage_node_type', 'column', '{"zh_CN": "字段", "en_US": "Column"}', '{"zh_CN": "表字段", "en_US": "Table column"}', 'column', 3, TRUE, 'active'),
('lineage_node_type', 'metric', '{"zh_CN": "指标", "en_US": "Metric"}', '{"zh_CN": "业务指标", "en_US": "Business metric"}', 'metric', 4, TRUE, 'active'),
('lineage_node_type', 'entity', '{"zh_CN": "实体", "en_US": "Entity"}', '{"zh_CN": "业务实体", "en_US": "Business entity"}', 'entity', 5, TRUE, 'active'),
('lineage_node_type', 'report', '{"zh_CN": "报表", "en_US": "Report"}', '{"zh_CN": "报表/看板", "en_US": "Report/Dashboard"}', 'report', 6, TRUE, 'active')
ON CONFLICT (group_code, code) DO NOTHING;

-- ==========================================
-- 4. 血缘边类型字典 (edge_type)
-- ==========================================
INSERT INTO sys_dict_group (code, name, description, category, is_tree, is_multiple, is_editable, status) VALUES
('edge_type', '{"zh_CN": "血缘边类型", "en_US": "Lineage Edge Type"}', '{"zh_CN": "血缘关系边类型", "en_US": "Lineage relationship edge types"}', 'business', FALSE, FALSE, FALSE, 'active')
ON CONFLICT (code) DO NOTHING;

INSERT INTO sys_dict_item (group_code, code, label, description, value, sort_order, is_system, status) VALUES
('edge_type', 'transforms', '{"zh_CN": "转换", "en_US": "Transforms"}', '{"zh_CN": "数据转换关系", "en_US": "Data transformation"}', 'transforms', 1, TRUE, 'active'),
('edge_type', 'depends_on', '{"zh_CN": "依赖", "en_US": "Depends On"}', '{"zh_CN": "依赖关系", "en_US": "Dependency relationship"}', 'depends_on', 2, TRUE, 'active'),
('edge_type', 'derives_from', '{"zh_CN": "派生", "en_US": "Derives From"}', '{"zh_CN": "派生关系", "en_US": "Derivation relationship"}', 'derives_from', 3, TRUE, 'active'),
('edge_type', 'feeds_into', '{"zh_CN": "流入", "en_US": "Feeds Into"}', '{"zh_CN": "数据流入", "en_US": "Data flows into"}', 'feeds_into', 4, TRUE, 'active')
ON CONFLICT (group_code, code) DO NOTHING;

-- ==========================================
-- 5. 数据源类型字典补充 (data_source_type)
-- ==========================================
INSERT INTO sys_dict_item (group_code, code, label, description, value, icon, sort_order, is_system, status) VALUES
('data_source_type', 'mysql', '{"zh_CN": "MySQL", "en_US": "MySQL"}', '{"zh_CN": "MySQL 数据库", "en_US": "MySQL database"}', 'mysql', 'DatabaseOutlined', 1, TRUE, 'active'),
('data_source_type', 'postgresql', '{"zh_CN": "PostgreSQL", "en_US": "PostgreSQL"}', '{"zh_CN": "PostgreSQL 数据库", "en_US": "PostgreSQL database"}', 'postgresql', 'DatabaseOutlined', 2, TRUE, 'active'),
('data_source_type', 'hive', '{"zh_CN": "Hive", "en_US": "Hive"}', '{"zh_CN": "Hive 数据仓库", "en_US": "Hive data warehouse"}', 'hive', 'CloudServerOutlined', 3, TRUE, 'active'),
('data_source_type', 'kafka', '{"zh_CN": "Kafka", "en_US": "Kafka"}', '{"zh_CN": "Kafka 消息队列", "en_US": "Kafka message queue"}', 'kafka', 'ThunderboltOutlined', 4, TRUE, 'active'),
('data_source_type', 'api', '{"zh_CN": "API 接口", "en_US": "API"}', '{"zh_CN": "REST API 接口", "en_US": "REST API endpoint"}', 'api', 'ApiOutlined', 5, TRUE, 'active'),
('data_source_type', 'file', '{"zh_CN": "文件", "en_US": "File"}', '{"zh_CN": "本地/远程文件", "en_US": "Local/remote file"}', 'file', 'FileOutlined', 6, TRUE, 'active')
ON CONFLICT (group_code, code) DO NOTHING;

-- ==========================================
-- 6. 同步方式字典补充 (sync_mode)
-- ==========================================
INSERT INTO sys_dict_item (group_code, code, label, description, value, sort_order, is_system, status) VALUES
('sync_mode', 'full', '{"zh_CN": "全量同步", "en_US": "Full Sync"}', '{"zh_CN": "全量数据同步", "en_US": "Full data synchronization"}', 'full', 1, TRUE, 'active'),
('sync_mode', 'incremental', '{"zh_CN": "增量同步", "en_US": "Incremental Sync"}', '{"zh_CN": "增量数据同步", "en_US": "Incremental data sync"}', 'incremental', 2, TRUE, 'active'),
('sync_mode', 'event_driven', '{"zh_CN": "事件驱动", "en_US": "Event Driven"}', '{"zh_CN": "事件触发同步", "en_US": "Event-triggered sync"}', 'event_driven', 3, TRUE, 'active')
ON CONFLICT (group_code, code) DO NOTHING;

-- ==========================================
-- 7. 报表类型字典 (report_type)
-- ==========================================
INSERT INTO sys_dict_group (code, name, description, category, is_tree, is_multiple, is_editable, status) VALUES
('report_type', '{"zh_CN": "报表类型", "en_US": "Report Type"}', '{"zh_CN": "报表分类", "en_US": "Report categories"}', 'business', FALSE, FALSE, FALSE, 'active')
ON CONFLICT (code) DO NOTHING;

INSERT INTO sys_dict_item (group_code, code, label, description, value, sort_order, is_system, status) VALUES
('report_type', 'weekly', '{"zh_CN": "周报", "en_US": "Weekly Report"}', '{"zh_CN": "经营周报", "en_US": "Weekly business report"}', 'weekly', 1, TRUE, 'active'),
('report_type', 'monthly', '{"zh_CN": "月报", "en_US": "Monthly Report"}', '{"zh_CN": "经营月报", "en_US": "Monthly business report"}', 'monthly', 2, TRUE, 'active'),
('report_type', 'special', '{"zh_CN": "专题报告", "en_US": "Special Report"}', '{"zh_CN": "专题分析报告", "en_US": "Special analysis report"}', 'special', 3, TRUE, 'active')
ON CONFLICT (group_code, code) DO NOTHING;

-- ==========================================
-- 8. 异常类型字典 (exception_type)
-- ==========================================
INSERT INTO sys_dict_group (code, name, description, category, is_tree, is_multiple, is_editable, status) VALUES
('exception_type', '{"zh_CN": "异常类型", "en_US": "Exception Type"}', '{"zh_CN": "数据异常分类", "en_US": "Data exception categories"}', 'business', TRUE, FALSE, FALSE, 'active')
ON CONFLICT (code) DO NOTHING;

INSERT INTO sys_dict_item (group_code, code, label, description, value, sort_order, is_system, status) VALUES
('exception_type', 'task_failure', '{"zh_CN": "任务失败", "en_US": "Task Failure"}', '{"zh_CN": "任务执行失败", "en_US": "Task execution failure"}', 'task_failure', 1, TRUE, 'active'),
('exception_type', 'data_quality', '{"zh_CN": "数据质量", "en_US": "Data Quality"}', '{"zh_CN": "数据质量异常", "en_US": "Data quality anomaly"}', 'data_quality', 2, TRUE, 'active'),
('exception_type', 'metric_fluctuation', '{"zh_CN": "指标波动", "en_US": "Metric Fluctuation"}', '{"zh_CN": "指标异常波动", "en_US": "Abnormal metric fluctuation"}', 'metric_fluctuation', 3, TRUE, 'active'),
('exception_type', 'connectivity', '{"zh_CN": "连接问题", "en_US": "Connectivity"}', '{"zh_CN": "数据源连接问题", "en_US": "Data source connectivity issue"}', 'connectivity', 4, TRUE, 'active'),
('exception_type', 'permission', '{"zh_CN": "权限问题", "en_US": "Permission"}', '{"zh_CN": "权限不足", "en_US": "Insufficient permission"}', 'permission', 5, TRUE, 'active'),
('exception_type', 'resource', '{"zh_CN": "资源问题", "en_US": "Resource"}', '{"zh_CN": "计算/存储资源不足", "en_US": "Insufficient compute/storage resource"}', 'resource', 6, TRUE, 'active')
ON CONFLICT (group_code, code) DO NOTHING;
