-- V011__align_auth_tables_with_base_entity.sql
-- 功能：为 sys_user/sys_role/sys_user_role/sys_permission 补齐 BaseEntity 标准字段，
--       确保 MyBatis-Plus BaseMapper 的 CRUD 能正常工作
-- 时间：2026-05-07
-- 作者：AxeXie

-- sys_user 补齐 created_by / updated_by
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS created_by VARCHAR(100) NOT NULL DEFAULT 'system';
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS updated_by VARCHAR(100) NOT NULL DEFAULT 'system';
COMMENT ON COLUMN sys_user.created_by IS '创建人';
COMMENT ON COLUMN sys_user.updated_by IS '最后更新人';

-- sys_role 补齐 is_deleted / created_by / updated_by
ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS created_by VARCHAR(100) NOT NULL DEFAULT 'system';
ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS updated_by VARCHAR(100) NOT NULL DEFAULT 'system';
COMMENT ON COLUMN sys_role.is_deleted IS '逻辑删除标识';
COMMENT ON COLUMN sys_role.created_by IS '创建人';
COMMENT ON COLUMN sys_role.updated_by IS '最后更新人';

-- sys_user_role 补齐 is_deleted / created_by / updated_by / updated_at
ALTER TABLE sys_user_role ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sys_user_role ADD COLUMN IF NOT EXISTS created_by VARCHAR(100) NOT NULL DEFAULT 'system';
ALTER TABLE sys_user_role ADD COLUMN IF NOT EXISTS updated_by VARCHAR(100) NOT NULL DEFAULT 'system';
ALTER TABLE sys_user_role ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE sys_user_role ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- sys_permission 补齐 is_deleted / created_by / updated_by / updated_at
ALTER TABLE sys_permission ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sys_permission ADD COLUMN IF NOT EXISTS created_by VARCHAR(100) NOT NULL DEFAULT 'system';
ALTER TABLE sys_permission ADD COLUMN IF NOT EXISTS updated_by VARCHAR(100) NOT NULL DEFAULT 'system';
ALTER TABLE sys_permission ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- 表注释
COMMENT ON TABLE sys_user IS '系统用户表';
COMMENT ON TABLE sys_role IS '系统角色表';
COMMENT ON TABLE sys_user_role IS '用户-角色关联表';
COMMENT ON TABLE sys_permission IS '角色权限表';
