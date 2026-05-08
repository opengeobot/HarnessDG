-- V008__seed_demo_ontology.sql
-- 功能：初始化演示本体数据（Order/Revenue/Channel）
-- 时间：2026-05-07
-- 作者：AxeXie

-- 插入演示实体
INSERT INTO ont_entity (code, name, description, entity_type, data_domain, owner, status, created_by, updated_by) VALUES
('order', '{"zh_CN":"订单","en_US":"Order"}', '{"zh_CN":"交易订单对象，记录用户购买行为","en_US":"Trade order object, recording user purchase behavior"}', 'fact', 'trade', 'admin', 'active', 'system', 'system'),
('customer', '{"zh_CN":"客户","en_US":"Customer"}', '{"zh_CN":"平台注册用户，消费主体","en_US":"Platform registered user, consumption subject"}', 'master', 'user', 'admin', 'active', 'system', 'system'),
('product', '{"zh_CN":"商品","en_US":"Product"}', '{"zh_CN":"平台售卖商品","en_US":"Platform product for sale"}', 'master', 'product', 'admin', 'active', 'system', 'system');

-- 插入演示维度
INSERT INTO ont_dimension (code, name, description, dimension_type, data_type, hierarchy_levels, created_by, updated_by) VALUES
('dim_date', '{"zh_CN":"日期","en_US":"Date"}', '{"zh_CN":"时间分析维度","en_US":"Time analysis dimension"}', 'time', 'date', '["year","quarter","month","week","day"]', 'system', 'system'),
('dim_channel', '{"zh_CN":"渠道","en_US":"Channel"}', '{"zh_CN":"销售渠道维度","en_US":"Sales channel dimension"}', 'channel', 'string', NULL, 'system', 'system'),
('dim_region', '{"zh_CN":"地区","en_US":"Region"}', '{"zh_CN":"地理区域维度","en_US":"Geographic region dimension"}', 'geo', 'string', '["country","province","city"]', 'system', 'system');

-- 插入演示指标
INSERT INTO ont_metric (code, name, description, entity_id, metric_type, agg_method, expression, grain, unit, data_domain, owner, status, created_by, updated_by) VALUES
('revenue', '{"zh_CN":"收入","en_US":"Revenue"}', '{"zh_CN":"订单实付金额总和","en_US":"Sum of actual payment amount from orders"}', 1, 'atomic', 'sum', 'SUM(order.payment_amount)', 'day', '¥', 'trade', 'admin', 'active', 'system', 'system'),
('order_count', '{"zh_CN":"订单数","en_US":"Order Count"}', '{"zh_CN":"有效订单计数","en_US":"Count of valid orders"}', 1, 'atomic', 'count', 'COUNT(order.id) WHERE order.status = ''paid''', 'day', '笔', 'trade', 'admin', 'active', 'system', 'system'),
('avg_order_value', '{"zh_CN":"客单价","en_US":"Average Order Value"}', '{"zh_CN":"收入/订单数","en_US":"Revenue divided by Order Count"}', 1, 'composite', 'avg', 'revenue / order_count', 'day', '¥', 'trade', 'admin', 'active', 'system', 'system');

-- 指标-维度关联
INSERT INTO ont_metric_dimension (metric_id, dimension_id, is_required, sort_order) VALUES
(1, 1, TRUE, 1),   -- revenue + date
(1, 2, FALSE, 2),  -- revenue + channel
(1, 3, FALSE, 3),  -- revenue + region
(2, 1, TRUE, 1),   -- order_count + date
(2, 2, FALSE, 2),  -- order_count + channel
(3, 1, TRUE, 1),   -- avg_order_value + date
(3, 2, FALSE, 2);  -- avg_order_value + channel

-- 实体关系
INSERT INTO ont_relation (source_entity_id, target_entity_id, relation_type, name, cardinality, created_by, updated_by) VALUES
(2, 1, 'has_many', '{"zh_CN":"客户下单","en_US":"Customer places Order"}', '1:N', 'system', 'system'),
(1, 3, 'references', '{"zh_CN":"订单包含商品","en_US":"Order contains Product"}', 'N:M', 'system', 'system');
