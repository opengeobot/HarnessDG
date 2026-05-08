-- V007__seed_dict_data.sql
-- 功能：初始化核心数据字典（10个分组 + 字典项）
-- 时间：2026-05-07
-- 作者：AxeXie

-- ========== 字典分组 ==========
INSERT INTO sys_dict_group (code, name, description, category, is_tree, is_multiple, is_editable) VALUES
('task_type', '{"zh_CN":"任务类型","en_US":"Task Type"}', '{"zh_CN":"业务任务分类","en_US":"Business task classification"}', 'business', FALSE, FALSE, FALSE),
('task_status', '{"zh_CN":"任务状态","en_US":"Task Status"}', '{"zh_CN":"任务执行状态","en_US":"Task execution status"}', 'system', FALSE, FALSE, FALSE),
('asset_status', '{"zh_CN":"资产状态","en_US":"Asset Status"}', '{"zh_CN":"数据资产生命周期状态","en_US":"Data asset lifecycle status"}', 'system', FALSE, FALSE, FALSE),
('metric_type', '{"zh_CN":"指标类型","en_US":"Metric Type"}', '{"zh_CN":"指标分类","en_US":"Metric classification"}', 'business', FALSE, FALSE, TRUE),
('metric_agg_method', '{"zh_CN":"聚合方式","en_US":"Aggregation Method"}', '{"zh_CN":"指标聚合函数","en_US":"Metric aggregation function"}', 'business', FALSE, FALSE, FALSE),
('dimension_type', '{"zh_CN":"维度类型","en_US":"Dimension Type"}', '{"zh_CN":"分析维度分类","en_US":"Analysis dimension classification"}', 'business', FALSE, FALSE, TRUE),
('time_granularity', '{"zh_CN":"时间粒度","en_US":"Time Granularity"}', '{"zh_CN":"调度频率与分析粒度","en_US":"Schedule frequency and analysis granularity"}', 'business', FALSE, FALSE, FALSE),
('data_domain', '{"zh_CN":"数据域","en_US":"Data Domain"}', '{"zh_CN":"业务数据域分类","en_US":"Business data domain classification"}', 'business', TRUE, FALSE, TRUE),
('priority_level', '{"zh_CN":"优先级","en_US":"Priority Level"}', '{"zh_CN":"任务优先级","en_US":"Task priority"}', 'system', FALSE, FALSE, FALSE),
('data_source_type', '{"zh_CN":"数据源类型","en_US":"Data Source Type"}', '{"zh_CN":"接入数据源类型","en_US":"Data source type for ingestion"}', 'business', FALSE, FALSE, TRUE);

-- ========== task_type 字典项 ==========
INSERT INTO sys_dict_item (group_code, code, label, description, value, icon, sort_order, is_system) VALUES
('task_type', 'data_ingestion', '{"zh_CN":"接数据","en_US":"Ingest Data"}', '{"zh_CN":"接入新数据源至平台","en_US":"Connect new data sources to platform"}', 'data_ingestion', 'DatabaseOutlined', 1, TRUE),
('task_type', 'build_metric', '{"zh_CN":"建指标","en_US":"Build Metric"}', '{"zh_CN":"定义并发布业务指标","en_US":"Define and publish business metrics"}', 'build_metric', 'LineChartOutlined', 2, TRUE),
('task_type', 'ask_data', '{"zh_CN":"问数","en_US":"Ask Data"}', '{"zh_CN":"用自然语言查询数据","en_US":"Query data using natural language"}', 'ask_data', 'MessageOutlined', 3, TRUE),
('task_type', 'generate_report', '{"zh_CN":"生成周报","en_US":"Generate Report"}', '{"zh_CN":"自动生成经营报告","en_US":"Auto-generate business reports"}', 'generate_report', 'FileTextOutlined', 4, TRUE),
('task_type', 'diagnose_exception', '{"zh_CN":"排查异常","en_US":"Diagnose Issues"}', '{"zh_CN":"智能定位数据问题","en_US":"Intelligently locate data issues"}', 'diagnose_exception', 'BugOutlined', 5, TRUE),
('task_type', 'request_permission', '{"zh_CN":"申请权限","en_US":"Request Access"}', '{"zh_CN":"申请数据访问权限","en_US":"Request data access permissions"}', 'request_permission', 'KeyOutlined', 6, TRUE);

-- ========== task_status 字典项 ==========
INSERT INTO sys_dict_item (group_code, code, label, value, color, icon, sort_order, is_system) VALUES
('task_status', 'draft', '{"zh_CN":"草稿","en_US":"Draft"}', 'draft', '#8C8C8C', 'EditOutlined', 1, TRUE),
('task_status', 'pending', '{"zh_CN":"待执行","en_US":"Pending"}', 'pending', '#1890FF', 'ClockCircleOutlined', 2, TRUE),
('task_status', 'running', '{"zh_CN":"执行中","en_US":"Running"}', 'running', '#1A365D', 'SyncOutlined', 3, TRUE),
('task_status', 'pending_approval', '{"zh_CN":"待审批","en_US":"Pending Approval"}', 'pending_approval', '#FA8C16', 'AuditOutlined', 4, TRUE),
('task_status', 'completed', '{"zh_CN":"已完成","en_US":"Completed"}', 'completed', '#52C41A', 'CheckCircleOutlined', 5, TRUE),
('task_status', 'failed', '{"zh_CN":"失败","en_US":"Failed"}', 'failed', '#FF4D4F', 'CloseCircleOutlined', 6, TRUE),
('task_status', 'cancelled', '{"zh_CN":"已取消","en_US":"Cancelled"}', 'cancelled', '#BFBFBF', 'StopOutlined', 7, TRUE);

-- ========== asset_status 字典项 ==========
INSERT INTO sys_dict_item (group_code, code, label, value, color, icon, sort_order, is_system) VALUES
('asset_status', 'draft', '{"zh_CN":"草稿","en_US":"Draft"}', 'draft', '#8C8C8C', 'EditOutlined', 1, TRUE),
('asset_status', 'pending_approval', '{"zh_CN":"待审批","en_US":"Pending Approval"}', 'pending_approval', '#FA8C16', 'ClockCircleOutlined', 2, TRUE),
('asset_status', 'active', '{"zh_CN":"已发布","en_US":"Active"}', 'active', '#52C41A', 'CheckCircleOutlined', 3, TRUE),
('asset_status', 'deprecated', '{"zh_CN":"已废弃","en_US":"Deprecated"}', 'deprecated', '#BFBFBF', 'StopOutlined', 4, TRUE),
('asset_status', 'archived', '{"zh_CN":"已归档","en_US":"Archived"}', 'archived', '#D9D9D9', 'InboxOutlined', 5, TRUE);

-- ========== metric_type 字典项 ==========
INSERT INTO sys_dict_item (group_code, code, label, description, value, sort_order, is_system) VALUES
('metric_type', 'atomic', '{"zh_CN":"原子指标","en_US":"Atomic Metric"}', '{"zh_CN":"基于单一事实的基础度量","en_US":"Basic measure based on a single fact"}', 'atomic', 1, TRUE),
('metric_type', 'derived', '{"zh_CN":"派生指标","en_US":"Derived Metric"}', '{"zh_CN":"基于原子指标加维度约束","en_US":"Atomic metric with dimensional constraints"}', 'derived', 2, TRUE),
('metric_type', 'composite', '{"zh_CN":"复合指标","en_US":"Composite Metric"}', '{"zh_CN":"多指标组合计算","en_US":"Calculated from multiple metrics"}', 'composite', 3, TRUE);

-- ========== metric_agg_method 字典项 ==========
INSERT INTO sys_dict_item (group_code, code, label, value, sort_order, is_system) VALUES
('metric_agg_method', 'sum', '{"zh_CN":"求和","en_US":"Sum"}', 'sum', 1, TRUE),
('metric_agg_method', 'count', '{"zh_CN":"计数","en_US":"Count"}', 'count', 2, TRUE),
('metric_agg_method', 'avg', '{"zh_CN":"平均值","en_US":"Average"}', 'avg', 3, TRUE),
('metric_agg_method', 'max', '{"zh_CN":"最大值","en_US":"Max"}', 'max', 4, TRUE),
('metric_agg_method', 'min', '{"zh_CN":"最小值","en_US":"Min"}', 'min', 5, TRUE),
('metric_agg_method', 'count_distinct', '{"zh_CN":"去重计数","en_US":"Count Distinct"}', 'count_distinct', 6, TRUE);

-- ========== dimension_type 字典项 ==========
INSERT INTO sys_dict_item (group_code, code, label, value, sort_order, is_system) VALUES
('dimension_type', 'time', '{"zh_CN":"时间维度","en_US":"Time"}', 'time', 1, TRUE),
('dimension_type', 'geo', '{"zh_CN":"地理维度","en_US":"Geography"}', 'geo', 2, TRUE),
('dimension_type', 'channel', '{"zh_CN":"渠道维度","en_US":"Channel"}', 'channel', 3, TRUE),
('dimension_type', 'category', '{"zh_CN":"分类维度","en_US":"Category"}', 'category', 4, TRUE),
('dimension_type', 'user_attr', '{"zh_CN":"用户属性","en_US":"User Attribute"}', 'user_attr', 5, TRUE);

-- ========== time_granularity 字典项 ==========
INSERT INTO sys_dict_item (group_code, code, label, value, sort_order, is_system) VALUES
('time_granularity', 'minute', '{"zh_CN":"分钟","en_US":"Minute"}', 'minute', 1, TRUE),
('time_granularity', 'hour', '{"zh_CN":"小时","en_US":"Hour"}', 'hour', 2, TRUE),
('time_granularity', 'day', '{"zh_CN":"天","en_US":"Day"}', 'day', 3, TRUE),
('time_granularity', 'week', '{"zh_CN":"周","en_US":"Week"}', 'week', 4, TRUE),
('time_granularity', 'month', '{"zh_CN":"月","en_US":"Month"}', 'month', 5, TRUE),
('time_granularity', 'quarter', '{"zh_CN":"季度","en_US":"Quarter"}', 'quarter', 6, TRUE),
('time_granularity', 'year', '{"zh_CN":"年","en_US":"Year"}', 'year', 7, TRUE);

-- ========== data_domain 字典项（树形） ==========
INSERT INTO sys_dict_item (group_code, code, label, value, sort_order, is_system) VALUES
('data_domain', 'trade', '{"zh_CN":"交易域","en_US":"Trade"}', 'trade', 1, TRUE),
('data_domain', 'user', '{"zh_CN":"用户域","en_US":"User"}', 'user', 2, TRUE),
('data_domain', 'product', '{"zh_CN":"商品域","en_US":"Product"}', 'product', 3, TRUE),
('data_domain', 'marketing', '{"zh_CN":"营销域","en_US":"Marketing"}', 'marketing', 4, TRUE),
('data_domain', 'finance', '{"zh_CN":"财务域","en_US":"Finance"}', 'finance', 5, TRUE);

-- ========== priority_level 字典项 ==========
INSERT INTO sys_dict_item (group_code, code, label, value, color, sort_order, is_system) VALUES
('priority_level', 'low', '{"zh_CN":"低","en_US":"Low"}', 'low', '#8C8C8C', 1, TRUE),
('priority_level', 'medium', '{"zh_CN":"中","en_US":"Medium"}', 'medium', '#1890FF', 2, TRUE),
('priority_level', 'high', '{"zh_CN":"高","en_US":"High"}', 'high', '#FA8C16', 3, TRUE),
('priority_level', 'critical', '{"zh_CN":"紧急","en_US":"Critical"}', 'critical', '#FF4D4F', 4, TRUE);

-- ========== data_source_type 字典项 ==========
INSERT INTO sys_dict_item (group_code, code, label, value, icon, sort_order, is_system) VALUES
('data_source_type', 'postgresql', '{"zh_CN":"PostgreSQL","en_US":"PostgreSQL"}', 'postgresql', 'DatabaseOutlined', 1, TRUE),
('data_source_type', 'mysql', '{"zh_CN":"MySQL","en_US":"MySQL"}', 'mysql', 'DatabaseOutlined', 2, TRUE),
('data_source_type', 'api', '{"zh_CN":"REST API","en_US":"REST API"}', 'api', 'ApiOutlined', 3, TRUE),
('data_source_type', 'csv', '{"zh_CN":"CSV文件","en_US":"CSV File"}', 'csv', 'FileOutlined', 4, TRUE),
('data_source_type', 'kafka', '{"zh_CN":"Kafka","en_US":"Kafka"}', 'kafka', 'ThunderboltOutlined', 5, TRUE);
