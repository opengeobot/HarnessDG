-- ============================================================================
-- 功能: Wave N 数据集字典种子——dataset_task / dataset_language 类型与常用项 + i18n 文案。
-- 时间: 2026-07-11
-- 作者: AxeXie
-- ============================================================================

INSERT INTO system_dict_type (dict_type_id, dict_code, name, i18n_key, description)
SELECT 'dct_' || dict_code, dict_code, name, 'dict.' || dict_code, descr
FROM (VALUES
    ('dataset_task',     '数据集任务', '数据集 ML/NLP 任务类型'),
    ('dataset_language', '数据集语言', '数据集主要语言或代码混合标识')
) AS seed(dict_code, name, descr)
ON CONFLICT (dict_code) DO NOTHING;

INSERT INTO system_dict_item (dict_item_id, dict_code, item_code, i18n_key, sort_order)
SELECT 'dct_' || dict_code || '_' || item_code,
       dict_code,
       item_code,
       'dict.' || dict_code || '.' || item_code,
       sort_order
FROM (VALUES
    ('dataset_task', 'classification',       0),
    ('dataset_task', 'detection',            1),
    ('dataset_task', 'segmentation',         2),
    ('dataset_task', 'summarization',        3),
    ('dataset_task', 'translation',          4),
    ('dataset_task', 'qa',                   5),
    ('dataset_task', 'generation',           6),
    ('dataset_task', 'instruction_tuning',   7),
    ('dataset_language', 'zh',           0),
    ('dataset_language', 'en',           1),
    ('dataset_language', 'ja',           2),
    ('dataset_language', 'ko',           3),
    ('dataset_language', 'de',           4),
    ('dataset_language', 'fr',           5),
    ('dataset_language', 'es',           6),
    ('dataset_language', 'code',         7),
    ('dataset_language', 'multilingual', 8)
) AS seed(dict_code, item_code, sort_order)
ON CONFLICT (dict_code, item_code) DO NOTHING;

INSERT INTO system_i18n_message (locale, message_key, message)
SELECT locale, message_key, message
FROM (VALUES
    ('zh-CN', 'dict.dataset_task', '数据集任务'),
    ('en-US', 'dict.dataset_task', 'Dataset Task'),
    ('zh-CN', 'dict.dataset_language', '数据集语言'),
    ('en-US', 'dict.dataset_language', 'Dataset Language'),
    ('zh-CN', 'dict.dataset_task.classification', '分类'),
    ('en-US', 'dict.dataset_task.classification', 'Classification'),
    ('zh-CN', 'dict.dataset_task.detection', '检测'),
    ('en-US', 'dict.dataset_task.detection', 'Detection'),
    ('zh-CN', 'dict.dataset_task.segmentation', '分割'),
    ('en-US', 'dict.dataset_task.segmentation', 'Segmentation'),
    ('zh-CN', 'dict.dataset_task.summarization', '摘要'),
    ('en-US', 'dict.dataset_task.summarization', 'Summarization'),
    ('zh-CN', 'dict.dataset_task.translation', '翻译'),
    ('en-US', 'dict.dataset_task.translation', 'Translation'),
    ('zh-CN', 'dict.dataset_task.qa', '问答'),
    ('en-US', 'dict.dataset_task.qa', 'Question Answering'),
    ('zh-CN', 'dict.dataset_task.generation', '生成'),
    ('en-US', 'dict.dataset_task.generation', 'Generation'),
    ('zh-CN', 'dict.dataset_task.instruction_tuning', '指令微调'),
    ('en-US', 'dict.dataset_task.instruction_tuning', 'Instruction Tuning'),
    ('zh-CN', 'dict.dataset_language.zh', '中文'),
    ('en-US', 'dict.dataset_language.zh', 'Chinese'),
    ('zh-CN', 'dict.dataset_language.en', '英文'),
    ('en-US', 'dict.dataset_language.en', 'English'),
    ('zh-CN', 'dict.dataset_language.ja', '日文'),
    ('en-US', 'dict.dataset_language.ja', 'Japanese'),
    ('zh-CN', 'dict.dataset_language.ko', '韩文'),
    ('en-US', 'dict.dataset_language.ko', 'Korean'),
    ('zh-CN', 'dict.dataset_language.de', '德文'),
    ('en-US', 'dict.dataset_language.de', 'German'),
    ('zh-CN', 'dict.dataset_language.fr', '法文'),
    ('en-US', 'dict.dataset_language.fr', 'French'),
    ('zh-CN', 'dict.dataset_language.es', '西班牙文'),
    ('en-US', 'dict.dataset_language.es', 'Spanish'),
    ('zh-CN', 'dict.dataset_language.code', '代码'),
    ('en-US', 'dict.dataset_language.code', 'Code'),
    ('zh-CN', 'dict.dataset_language.multilingual', '多语言'),
    ('en-US', 'dict.dataset_language.multilingual', 'Multilingual')
) AS seed(locale, message_key, message)
ON CONFLICT (locale, message_key) DO NOTHING;
