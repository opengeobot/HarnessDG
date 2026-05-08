-- V006__seed_roles_and_admin.sql
-- 功能：初始化角色和管理员账号
-- 时间：2026-05-07
-- 作者：AxeXie

-- 初始化角色
INSERT INTO sys_role (code, name, description, is_system) VALUES
('admin', '{"zh_CN":"系统管理员","en_US":"Administrator"}', '{"zh_CN":"系统全权限管理员","en_US":"Full system administrator"}', TRUE),
('data_developer', '{"zh_CN":"数据开发","en_US":"Data Developer"}', '{"zh_CN":"负责底层开发与模板沉淀","en_US":"Responsible for development and template management"}', TRUE),
('analyst', '{"zh_CN":"业务分析师","en_US":"Business Analyst"}', '{"zh_CN":"负责口径定义、指标设计与分析","en_US":"Responsible for metric definition and analysis"}', TRUE),
('business_user', '{"zh_CN":"业务人员","en_US":"Business User"}', '{"zh_CN":"通过任务中心完成数据操作","en_US":"Complete data tasks through task center"}', TRUE);

-- 初始化管理员账号（密码: admin123, BCrypt hash - $2b$10 版本）
INSERT INTO sys_user (username, password_hash, display_name, email, locale, status) VALUES
('admin', '$2b$10$tUiISj7PcctCa5RnVE0zUOqbLVO0tMbPBEXVWA.XW05ZOt1HBwcYu', '系统管理员', 'admin@harnessdg.local', 'zh_CN', 'active');

-- 为管理员分配角色
INSERT INTO sys_user_role (user_id, role_id, granted_by) VALUES
(1, 1, 'system');

-- 管理员角色赋予所有权限
INSERT INTO sys_permission (role_id, resource_type, resource_id, action, effect) VALUES
(1, '*', '*', '*', 'allow');

-- 分析师角色权限
INSERT INTO sys_permission (role_id, resource_type, resource_id, action, effect) VALUES
(3, 'entity', '*', 'view_definition', 'allow'),
(3, 'metric', '*', 'view_definition', 'allow'),
(3, 'metric', '*', 'edit_draft', 'allow'),
(3, 'metric', '*', 'submit_approval', 'allow'),
(3, 'task', '*', 'initiate_task', 'allow'),
(3, 'task', '*', 'view_result', 'allow');

-- 业务人员角色权限
INSERT INTO sys_permission (role_id, resource_type, resource_id, action, effect) VALUES
(4, 'entity', '*', 'view_definition', 'allow'),
(4, 'metric', '*', 'view_definition', 'allow'),
(4, 'task', '*', 'initiate_task', 'allow'),
(4, 'task', '*', 'view_result', 'allow');
