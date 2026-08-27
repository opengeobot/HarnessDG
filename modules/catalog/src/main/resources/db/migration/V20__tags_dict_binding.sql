-- V20__tags_dict_binding.sql
-- 标签字典化（仓库维护与标签字典化计划 §A）：model/dataset/studio 的 metadata
-- schema 升级 v2 —— tags 数组绑定 'tag' 字典（数组属性级 "taxonomy" 键，与
-- MetadataValidator.checkTaxonomy 读取方式一致），由管理后台字典统一管理；
-- 未注册字典项创建/更新时 422 unknown_taxonomy_value（active+disabled 均合法，
-- 保持「弃用值可用」语义）。v1 行保留不动（存量仓库与显式 v1 提交向后兼容）。
-- 单事务原子执行。

INSERT INTO resource_type_schema_versions
(type_key, version, metadata_schema, ui_schema, facet_definitions, file_policy,
 default_visibility, allowed_workflows, checksum, status, published_at)
SELECT v.type_key, 2,
       -- 仅改写 properties.tags：追加 "taxonomy":"tag"，其余字段原样继承
       jsonb_set(v.metadata_schema, '{properties,tags}',
                 (v.metadata_schema -> 'properties' -> 'tags') || '{"taxonomy": "tag"}'::jsonb),
       v.ui_schema, v.facet_definitions, v.file_policy,
       v.default_visibility, v.allowed_workflows,
       'seed-' || v.type_key || '-v2', 'published', now()
FROM resource_type_schema_versions v
WHERE v.version = 1
  AND v.type_key IN ('model', 'dataset', 'studio');

UPDATE resource_types
SET current_schema_version = 2
WHERE type_key IN ('model', 'dataset', 'studio');

-- 自检：三类均存在 v2 且 tags 已绑定字典；'tag' 字典已注册（V18 迁入）
DO $$
DECLARE missing BIGINT; dicts BIGINT;
BEGIN
    SELECT COUNT(*) INTO missing
    FROM resource_type_schema_versions
    WHERE version = 2 AND type_key IN ('model', 'dataset', 'studio')
      AND metadata_schema -> 'properties' -> 'tags' ->> 'taxonomy' = 'tag';
    IF missing <> 3 THEN
        RAISE EXCEPTION 'V20 迁移自检失败：预期 3 条 v2 schema 绑定 tag 字典，实际 %', missing;
    END IF;
    SELECT COUNT(*) INTO dicts FROM sys_dict WHERE dict_code = 'tag';
    IF dicts <> 1 THEN
        RAISE EXCEPTION 'V20 迁移自检失败：tag 字典不存在（V18 应已迁入）';
    END IF;
END $$;
