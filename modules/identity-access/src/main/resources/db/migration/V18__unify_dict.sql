-- V18__unify_dict.sql
-- 字典统一（字典统一+菜单权限重构计划 §一）：市场分类（9 类，V2/V4/V5）迁入
-- sys_dict/sys_dict_item 单一字典体系；字典项新增 parent_id 支持两级树；
-- model_profiles/dataset_profiles 的 taxonomy_values 外键换算为 sys_dict_item
-- 外键；迁移完成后退役（DROP）taxonomy 双表。单事务原子执行。

-- ---------- 1. 字典项支持两级层级 ----------
ALTER TABLE sys_dict_item ADD COLUMN parent_id BIGINT REFERENCES sys_dict_item(id);
CREATE INDEX ix_sys_dict_item_parent ON sys_dict_item(parent_id);

-- ---------- 2. 分类定义迁移：taxonomies → sys_dict ----------
INSERT INTO sys_dict (public_id, dict_code, name, description)
SELECT gen_random_uuid(), t.taxonomy_key, t.display_name, '市场筛选分类（由 taxonomy 迁移）'
FROM taxonomies t
ORDER BY t.id;

-- ---------- 3. 分类值迁移：taxonomy_values → sys_dict_item ----------
-- 旧 id → (taxonomy_key, value_key, 旧父 id) 映射表，供回填 new_id/parent_id 与
-- profile 外键换算使用；(taxonomy_key, value_key) 全局唯一（源表同分类内唯一 +
-- taxonomy_key 唯一），可安全反查新 id。
CREATE TEMP TABLE tv_map AS
SELECT tv.id AS old_id, t.taxonomy_key, tv.value_key, tv.parent_id AS old_parent_id,
       CAST(NULL AS BIGINT) AS new_id
FROM taxonomy_values tv
JOIN taxonomies t ON tv.taxonomy_id = t.id;

INSERT INTO sys_dict_item (dict_id, item_value, label_zh, label_en, sort_order, status)
SELECT d.id, tv.value_key, tv.display_name, tv.display_name, tv.sort_order,
       CASE tv.status WHEN 'deprecated' THEN 'disabled' ELSE 'active' END
FROM taxonomy_values tv
JOIN taxonomies t ON tv.taxonomy_id = t.id
JOIN sys_dict d ON d.dict_code = t.taxonomy_key
ORDER BY tv.id;

UPDATE tv_map m SET new_id = i.id
FROM sys_dict_item i
JOIN sys_dict d ON i.dict_id = d.id
WHERE d.dict_code = m.taxonomy_key AND i.item_value = m.value_key;

-- 层级回填（此时全部项已插入，父子顺序无关）
UPDATE sys_dict_item i
SET parent_id = pm.new_id
FROM tv_map m
JOIN tv_map pm ON pm.old_id = m.old_parent_id
JOIN sys_dict d ON d.dict_code = m.taxonomy_key
WHERE i.dict_id = d.id AND i.item_value = m.value_key;

-- ---------- 4. profile 外键切换：taxonomy_values → sys_dict_item ----------
-- 动态摘除所有指向 taxonomy_values 的外键（不硬编码约束名）
DO $$
DECLARE c RECORD;
BEGIN
    FOR c IN
        SELECT con.conname, con.conrelid::regclass::text AS tbl
        FROM pg_constraint con
        WHERE con.contype = 'f' AND con.confrelid = 'taxonomy_values'::regclass
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', c.tbl, c.conname);
    END LOOP;
END $$;

-- 旧值 → 新字典项 id 换算（未命中映射的置 NULL，由下方自检兜底暴露）
UPDATE model_profiles mp SET task_value_id = m.new_id
FROM tv_map m WHERE mp.task_value_id = m.old_id;
UPDATE model_profiles mp SET architecture_value_id = m.new_id
FROM tv_map m WHERE mp.architecture_value_id = m.old_id;
UPDATE model_profiles mp SET primary_language_value_id = m.new_id
FROM tv_map m WHERE mp.primary_language_value_id = m.old_id;
UPDATE dataset_profiles dp SET task_value_id = m.new_id
FROM tv_map m WHERE dp.task_value_id = m.old_id;

-- 自检：四列非空引用必须全部落在 sys_dict_item 内，否则整体回滚
DO $$
DECLARE bad BIGINT;
BEGIN
    SELECT COUNT(*) INTO bad FROM (
        SELECT task_value_id AS v FROM model_profiles WHERE task_value_id IS NOT NULL
        UNION ALL SELECT architecture_value_id FROM model_profiles WHERE architecture_value_id IS NOT NULL
        UNION ALL SELECT primary_language_value_id FROM model_profiles WHERE primary_language_value_id IS NOT NULL
        UNION ALL SELECT task_value_id FROM dataset_profiles WHERE task_value_id IS NOT NULL
    ) x
    LEFT JOIN sys_dict_item i ON i.id = x.v
    WHERE i.id IS NULL;
    IF bad > 0 THEN
        RAISE EXCEPTION 'V18 迁移自检失败：profile 存在 % 条未映射到 sys_dict_item 的引用', bad;
    END IF;
END $$;

ALTER TABLE model_profiles
    ADD CONSTRAINT fk_model_profiles_task_dict_item FOREIGN KEY (task_value_id) REFERENCES sys_dict_item(id);
ALTER TABLE model_profiles
    ADD CONSTRAINT fk_model_profiles_arch_dict_item FOREIGN KEY (architecture_value_id) REFERENCES sys_dict_item(id);
ALTER TABLE model_profiles
    ADD CONSTRAINT fk_model_profiles_lang_dict_item FOREIGN KEY (primary_language_value_id) REFERENCES sys_dict_item(id);
ALTER TABLE dataset_profiles
    ADD CONSTRAINT fk_dataset_profiles_task_dict_item FOREIGN KEY (task_value_id) REFERENCES sys_dict_item(id);

-- ---------- 5. 退役 taxonomy 双表（数据已全量迁出；aliases/metadata/valid_* 无实际数据） ----------
DROP TABLE taxonomy_values;
DROP TABLE taxonomies;
