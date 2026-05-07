-- HarnessDG PostgreSQL 初始化脚本
-- 功能：启用 AGE 和 pgvector 扩展，创建本体图
-- 时间：2026-05-07
-- 作者：AxeXie

-- 启用扩展（如果 pgvector 不存在，需要先安装 postgresql-16-pgvector 包）
CREATE EXTENSION IF NOT EXISTS age;

-- pgvector 可能需要额外安装，尝试启用
-- 如果容器未内置 pgvector，会在 docker-compose 中通过 init 脚本安装
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS vector;
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'pgvector extension not available in this PostgreSQL image';
END $$;

-- 加载 AGE 到搜索路径
SET search_path = ag_catalog, "$user", public;

-- 创建本体关系图
SELECT create_graph('ontology_graph');

-- 恢复默认搜索路径
SET search_path = "$user", public;
