-- V4__catalog_taxonomy_seed.sql
-- taxonomy 种子：demo options.py 枚举作为 v1 初始种子（03 §4.2），value_key 稳定、display_name 可本地化。

INSERT INTO taxonomies (taxonomy_key, display_name) VALUES
('framework',    '框架'),
('license',      '许可证'),
('architecture', '模型架构'),
('language',     '语言'),
('tag',          '标签'),
('capability',   '能力'),
('scene',        '创空间场景'),
('model_task',   '模型任务'),
('dataset_task', '数据集任务');

-- ---------- 平铺分类 ----------
DO $$
DECLARE t BIGINT;
BEGIN
  SELECT id INTO t FROM taxonomies WHERE taxonomy_key = 'framework';
  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, sort_order) VALUES
  (t, 'gguf', 'GGUF', 0), (t, 'diffusers', 'Diffusers', 1), (t, 'lora', 'LoRA', 2),
  (t, 'llamafile', 'Llamafile', 3), (t, 'onnx', 'ONNX', 4), (t, 'pytorch', 'PyTorch', 5),
  (t, 'safetensors', 'Safetensors', 6), (t, 'tensorflow', 'TensorFlow', 7),
  (t, 'transformers', 'Transformers', 8), (t, 'xinference', 'Xinference', 9),
  (t, 'mlx', 'MLX', 10), (t, 'openvino', 'OpenVINO', 11),
  (t, 'sentence-transformers', 'sentence-transformers', 12);

  SELECT id INTO t FROM taxonomies WHERE taxonomy_key = 'license';
  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, sort_order) VALUES
  (t, 'apache-2.0', 'Apache License 2.0', 0), (t, 'gpl-2.0', 'GPL-2.0', 1),
  (t, 'lgpl-3.0', 'LGPL-3.0', 2), (t, 'mit', 'MIT License', 3),
  (t, 'lgpl-2.1', 'LGPL-2.1', 4), (t, 'gpl-3.0', 'GPL-3.0', 5),
  (t, 'afl-3.0', 'AFL-3.0', 6), (t, 'ecl-2.0', 'ECL-2.0', 7),
  (t, 'cc-by-4.0', 'CC-BY-4.0', 8), (t, 'creativeml-openrail-m', 'creativeml-openrail-m', 9),
  (t, 'cc-by-nc-nd', 'CC-BY-NC-ND', 10), (t, 'agpl-3.0', 'agpl-3.0', 11);

  SELECT id INTO t FROM taxonomies WHERE taxonomy_key = 'architecture';
  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, sort_order) VALUES
  (t, 'qwen2', 'qwen2', 0), (t, 'qwen3', 'qwen3', 1), (t, 'llama', 'llama', 2),
  (t, 'llama2', 'llama2', 3), (t, 'llama3', 'llama3', 4), (t, 'llama4', 'llama4', 5),
  (t, 'deepseek-v2', 'deepseek_v2', 6), (t, 'deepseek-v3', 'deepseek_v3', 7),
  (t, 'bert', 'bert', 8), (t, 'qwen', 'qwen', 9),
  (t, 'stable-diffusion', 'stable_diffusion', 10), (t, 'chatglm', 'chatglm', 11),
  (t, 'qwen2-5-vl', 'qwen2_5_vl', 12), (t, 'internlm2', 'internlm2', 13),
  (t, 'mistral', 'mistral', 14);

  SELECT id INTO t FROM taxonomies WHERE taxonomy_key = 'language';
  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, sort_order) VALUES
  (t, 'zh', '中文', 0), (t, 'en', '英文', 1), (t, 'ja', '日语', 2),
  (t, 'ko', '韩语', 3), (t, 'ru', '俄语', 4), (t, 'de', '德语', 5);

  SELECT id INTO t FROM taxonomies WHERE taxonomy_key = 'tag';
  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, sort_order) VALUES
  (t, 'alibaba', 'Alibaba', 0), (t, 'chat', 'chat', 1), (t, 'code', 'code', 2),
  (t, 'ner', 'ner', 3), (t, 'tts', 'tts', 4), (t, 'mteb', 'mteb', 5),
  (t, 'gptq', 'gptq', 6), (t, '4bit', '4bit', 7), (t, '8bit', '8bit', 8);

  SELECT id INTO t FROM taxonomies WHERE taxonomy_key = 'scene';
  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, sort_order) VALUES
  (t, 'workplace', '职场', 0), (t, 'education', '教育', 1), (t, 'marketing', '营销', 2),
  (t, 'life', '生活', 3), (t, 'emotion', '情感', 4), (t, 'fun', '趣味', 5),
  (t, 'creation', '创作', 6), (t, 'gaming', '游戏', 7), (t, 'coding', '编程', 8),
  (t, 'health', '健康', 9), (t, 'pet', '宠物', 10), (t, 'travel', '出行', 11),
  (t, 'finance', '金融', 12), (t, 'social', '社交', 13), (t, 'data', '数据', 14),
  (t, 'legal', '法律', 15), (t, 'aigc', 'AIGC', 16), (t, 'mcp', 'MCP', 17),
  (t, 'hackathon2026', 'Hackathon2026', 18);
END $$;

-- ---------- capability（含 CAP_OPTIONS 子选项） ----------
DO $$
DECLARE t BIGINT; p BIGINT;
BEGIN
  SELECT id INTO t FROM taxonomies WHERE taxonomy_key = 'capability';
  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, sort_order) VALUES
  (t, 'support-experience', '支持体验', 0) RETURNING id INTO p;
  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, parent_id, sort_order) VALUES
  (t, 'api-inference', 'API-Inference', p, 0),
  (t, 'model-demo', '模型 Demo 体验', p, 1),
  (t, 'restful-api-experience', 'Restful API 体验', p, 2);

  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, sort_order) VALUES
  (t, 'support-training', '支持训练', 1) RETURNING id INTO p;
  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, parent_id, sort_order) VALUES
  (t, 'flextrain', 'FlexTrain 训练微调', p, 0),
  (t, 'sdk-train', 'SDK 编程训练微调', p, 1),
  (t, 'pai-gallery-train', 'PAI Model Gallery 训练', p, 2);

  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, sort_order) VALUES
  (t, 'support-evaluation', '支持评测', 2) RETURNING id INTO p;
  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, parent_id, sort_order) VALUES
  (t, 'pivot-eval', 'PivotEval 评测服务', p, 0),
  (t, 'pai-gallery-eval', 'PAI Model Gallery 评测', p, 1);

  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, sort_order) VALUES
  (t, 'support-deployment', '支持部署', 3) RETURNING id INTO p;
  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, parent_id, sort_order) VALUES
  (t, 'swing-deploy', 'SwingDeploy 快速部署', p, 0),
  (t, 'pai-gallery-deploy', 'PAI Model Gallery 部署', p, 1);
END $$;
