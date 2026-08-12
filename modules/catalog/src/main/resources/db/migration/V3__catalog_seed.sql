-- V3__catalog_seed.sql
-- 资源类型注册表种子（03 §2.2）：model/dataset/studio，v1 Schema published。
-- published 版本不可原地修改；升级必须插入新版本并原子切换 current_schema_version。

INSERT INTO resource_types (type_key, display_name, capabilities, handler_key, renderer_key) VALUES
('model',   '模型',   '["repo:read","repo:write","artifact:upload","preview:generate"]', 'model-handler',   'model'),
('dataset', '数据集', '["repo:read","repo:write","artifact:upload","preview:generate"]', 'dataset-handler', 'dataset'),
('studio',  '创空间', '["repo:read","repo:write","artifact:upload"]',                     'studio-handler',  'studio');

INSERT INTO resource_type_schema_versions
(type_key, version, metadata_schema, ui_schema, facet_definitions, file_policy, default_visibility, allowed_workflows, checksum, status, published_at)
VALUES
('model', 1,
 '{"type":"object","required":["license"],"properties":{"license":{"type":"string","maxLength":128},"tags":{"type":"array","items":{"type":"string","maxLength":64},"maxItems":32},"framework":{"type":"string","maxLength":64},"architecture":{"type":"string","maxLength":64},"language":{"type":"string","maxLength":32},"task":{"type":"string","maxLength":64},"capabilities":{"type":"array","items":{"type":"string","maxLength":64},"maxItems":16},"apiStatus":{"type":"string","maxLength":32},"parameterCount":{"type":"integer","minimum":0},"parameterUnit":{"type":"string","maxLength":16},"deployable":{"type":"boolean"},"mcpCompatible":{"type":"boolean"}},"additionalProperties":false}',
 '{"order":["task","framework","architecture","language","license","tags","capabilities"]}',
 '[{"key":"task","type":"text","filterable":true,"taxonomy":"model_task"},{"key":"framework","type":"text","filterable":true,"taxonomy":"framework","multi":true},{"key":"license","type":"text","filterable":true,"taxonomy":"license"},{"key":"architecture","type":"text","filterable":true,"taxonomy":"architecture"},{"key":"language","type":"text","filterable":true,"taxonomy":"language"},{"key":"tag","type":"text","filterable":true,"taxonomy":"tag","multi":true},{"key":"capability","type":"text","filterable":true,"taxonomy":"capability","multi":true},{"key":"apiStatus","type":"text","filterable":true},{"key":"deployable","type":"boolean","filterable":true},{"key":"mcpCompatible","type":"boolean","filterable":true}]',
 '{"maxFileSizeBytes":53687091200,"maxPartSizeBytes":5368709120,"allowedContentTypes":["application/octet-stream","text/plain","application/json"]}',
 'public', '["upload","delete"]', 'seed-model-v1', 'published', now()),
('dataset', 1,
 '{"type":"object","required":["license"],"properties":{"license":{"type":"string","maxLength":128},"tags":{"type":"array","items":{"type":"string","maxLength":64},"maxItems":32},"task":{"type":"string","maxLength":64},"estimatedRows":{"type":"integer","minimum":0},"dataFormats":{"type":"array","items":{"type":"string","maxLength":32},"maxItems":16},"sensitivityLevel":{"type":"string","maxLength":32},"previewPolicy":{"type":"string","maxLength":32}},"additionalProperties":false}',
 '{"order":["task","license","dataFormats","tags"]}',
 '[{"key":"task","type":"text","filterable":true,"taxonomy":"dataset_task"},{"key":"license","type":"text","filterable":true,"taxonomy":"license"},{"key":"tag","type":"text","filterable":true,"taxonomy":"tag","multi":true},{"key":"dataFormat","type":"text","filterable":true,"multi":true}]',
 '{"maxFileSizeBytes":53687091200,"maxPartSizeBytes":5368709120,"allowedContentTypes":["application/octet-stream","text/plain","application/json","text/csv","application/x-parquet"]}',
 'public', '["upload","delete","preview"]', 'seed-dataset-v1', 'published', now()),
('studio', 1,
 '{"type":"object","properties":{"tags":{"type":"array","items":{"type":"string","maxLength":64},"maxItems":32},"scenes":{"type":"array","items":{"type":"string","maxLength":64},"maxItems":8},"runtimeType":{"type":"string","maxLength":32},"coverObjectKey":{"type":"string","maxLength":256}},"additionalProperties":false}',
 '{"order":["scenes","tags"]}',
 '[{"key":"scene","type":"text","filterable":true,"taxonomy":"scene","multi":true},{"key":"tag","type":"text","filterable":true,"taxonomy":"tag","multi":true}]',
 '{"maxFileSizeBytes":10737418240,"maxPartSizeBytes":1073741824,"allowedContentTypes":["application/octet-stream","text/plain","application/json","image/png","image/jpeg"]}',
 'public', '["upload","delete"]', 'seed-studio-v1', 'published', now());

UPDATE resource_types SET current_schema_version = 1;
