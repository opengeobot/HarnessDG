-- V5__catalog_task_tree_seed.sql
-- 任务树种子：demo options.py 的 MODEL_TASK_TREE / DATASET_TASK_TREE（分类节点 + 叶子）。

DO $$
DECLARE t BIGINT; p BIGINT;
BEGIN
  SELECT id INTO t FROM taxonomies WHERE taxonomy_key = 'model_task';

  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, sort_order) VALUES
  (t, 'multimodal', '多模态', 0) RETURNING id INTO p;
  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, parent_id, sort_order) VALUES
  (t, 'image-text-understanding', '图文理解', p, 0), (t, 'visual-grounding', '视觉定位', p, 1),
  (t, 'visual-multimodal-understanding', '视觉多模态理解', p, 2), (t, 'unified-multimodal', '统一多模态', p, 3),
  (t, 'multimodal-embedding', '多模态嵌入', p, 4), (t, 'visual-qa', '视觉问答', p, 5),
  (t, 'image-captioning', '图像字幕', p, 6), (t, 'video-understanding', '视频理解', p, 7),
  (t, 'video-qa', '视频问答', p, 8), (t, 'visual-reasoning', '视觉推理', p, 9),
  (t, 'image-text-retrieval', '图文检索', p, 10), (t, 'table-recognition', '表格识别', p, 11),
  (t, 'document-understanding', '文档理解', p, 12), (t, 'visual-grounding-count', '视觉接地', p, 13),
  (t, 'multimodal-dialogue', '多模态对话', p, 14), (t, 'multimodal-extraction', '多模态信息抽取', p, 15),
  (t, 'video-captioning', '视频字幕', p, 16), (t, 'video-description', '视频描述', p, 17),
  (t, 'action-recognition', '动作识别', p, 18), (t, 'image-editing', '图像编辑', p, 19),
  (t, 'visual-grounding-and-count', '视觉定位与计数', p, 20), (t, 'unified-multimodal-gen', '统一多模态生成', p, 21),
  (t, 'multimodal-reasoning', '多模态推理', p, 22), (t, 'multimodal-agent', '多模态智能体', p, 23),
  (t, 'vision-foundation', '视觉基础模型', p, 24), (t, 'multimodal-translation', '多模态翻译', p, 25);

  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, sort_order) VALUES
  (t, 'nlp', '自然语言处理', 1) RETURNING id INTO p;
  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, parent_id, sort_order) VALUES
  (t, 'text-generation', '文本生成', p, 0), (t, 'text-classification', '文本分类', p, 1),
  (t, 'ner', '命名实体识别', p, 2), (t, 'relation-extraction', '关系抽取', p, 3),
  (t, 'qa', '问答', p, 4), (t, 'summarization', '摘要', p, 5),
  (t, 'machine-translation', '机器翻译', p, 6), (t, 'dialogue', '对话', p, 7),
  (t, 'sentiment-analysis', '情感分析', p, 8), (t, 'word-embedding', '词向量', p, 9),
  (t, 'text-correction', '文本纠错', p, 10), (t, 'cloze', '完形填空', p, 11),
  (t, 'zero-shot', '零样本学习', p, 12), (t, 'sentence-similarity', '句子相似度', p, 13),
  (t, 'text-ranking', '文本排序', p, 14), (t, 'tokenization', '分词', p, 15),
  (t, 'pos-tagging', '词性标注', p, 16), (t, 'dependency-parsing', '依存句法分析', p, 17),
  (t, 'information-extraction', '信息抽取', p, 18), (t, 'text-to-image', '文本生成图片', p, 19),
  (t, 'smart-dialogue', '智能对话', p, 20);

  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, sort_order) VALUES
  (t, 'speech', '语音', 2) RETURNING id INTO p;
  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, parent_id, sort_order) VALUES
  (t, 'tts', '语音合成', p, 0), (t, 'asr', '语音识别', p, 1),
  (t, 'speech-signal-processing', '语音信号处理', p, 2), (t, 'speaker-verification', '说话人验证', p, 3),
  (t, 'speech-translation', '语音翻译', p, 4), (t, 'audio-classification', '音频分类', p, 5),
  (t, 'speaker-confirmation', '说话人确认', p, 6), (t, 'speech-analysis', '语音分析', p, 7),
  (t, 'music-generation', '音乐生成', p, 8), (t, 'voice-cloning', '语音克隆', p, 9),
  (t, 'voiceprint-recognition', '声纹识别', p, 10), (t, 'speech-emotion-recognition', '语音情绪识别', p, 11),
  (t, 'audio-understanding', '音频理解', p, 12), (t, 'speech-denoising', '语音降噪', p, 13),
  (t, 'audio-labeling', '音频分类与标注', p, 14), (t, 'speaker-diarization', '多说话人分离', p, 15),
  (t, 'keyword-detection', '关键词检测', p, 16), (t, 'vad', '语音活动检测', p, 17),
  (t, 'e2e-asr', '端到端语音识别', p, 18);

  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, sort_order) VALUES
  (t, 'cv', '计算机视觉', 3) RETURNING id INTO p;
  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, parent_id, sort_order) VALUES
  (t, 'image-classification', '图像分类', p, 0), (t, 'object-detection', '目标检测', p, 1),
  (t, 'image-segmentation', '图像分割', p, 2), (t, 'image-generation', '图像生成', p, 3),
  (t, 'face-recognition', '人脸识别', p, 4), (t, 'keypoint-detection', '关键点检测', p, 5),
  (t, 'ocr', 'OCR', p, 6), (t, 'image-enhancement', '图像增强', p, 7),
  (t, 'super-resolution', '图像超分辨率', p, 8), (t, 'image-editing-cv', '图像编辑', p, 9),
  (t, 'image-inpainting', '图像修复', p, 10), (t, 'text-to-image-cv', '文本生成图片', p, 11),
  (t, 'text-to-video', '文本生成视频', p, 12), (t, 'video-generation', '视频生成', p, 13),
  (t, 'image-style-transfer', '图像风格迁移', p, 14);

  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, sort_order) VALUES
  (t, 'scientific', '科学计算', 4) RETURNING id INTO p;
  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, parent_id, sort_order) VALUES
  (t, 'protein-structure-gen', '蛋白质结构生成', p, 0), (t, 'protein-function-prediction', '蛋白质功能预测', p, 1);
END $$;

DO $$
DECLARE t BIGINT; p BIGINT;
BEGIN
  SELECT id INTO t FROM taxonomies WHERE taxonomy_key = 'dataset_task';

  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, sort_order) VALUES
  (t, 'text', '文本', 0) RETURNING id INTO p;
  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, parent_id, sort_order) VALUES
  (t, 'text-classification', '文本分类', p, 0), (t, 'relation-extraction', '关系抽取', p, 1),
  (t, 'zero-shot', '零样本学习', p, 2), (t, 'machine-translation', '机器翻译', p, 3),
  (t, 'word-classification', '词分类', p, 4), (t, 'smart-dialogue', '智能对话', p, 5),
  (t, 'text-generation', '文本生成', p, 6), (t, 'table-qa', '表格问答', p, 7),
  (t, 'sentence-similarity', '句子相似度', p, 8), (t, 'multilingual', '多语言', p, 9),
  (t, 'cloze', '完形填空', p, 10), (t, 'summarization', '摘要总结', p, 11),
  (t, 'qa', '问答', p, 12);

  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, sort_order) VALUES
  (t, 'image', '图像', 1) RETURNING id INTO p;
  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, parent_id, sort_order) VALUES
  (t, 'image-classification', '图像分类', p, 0), (t, 'object-detection', '目标检测', p, 1),
  (t, 'image-segmentation', '图像分割', p, 2), (t, 'face-recognition', '人脸识别', p, 3),
  (t, 'keypoint-detection', '关键点检测', p, 4), (t, 'ocr', 'OCR', p, 5),
  (t, 'image-generation', '图像生成', p, 6), (t, 'image-enhancement', '图像增强', p, 7),
  (t, 'visual-grounding', '视觉定位', p, 8), (t, 'image-text-matching', '图文匹配', p, 9),
  (t, 'image-captioning', '图像字幕', p, 10), (t, 'scene-recognition', '场景识别', p, 11),
  (t, 'text-recognition', '文本识别', p, 12), (t, 'image-inpainting', '图像修复', p, 13),
  (t, 'image-retrieval', '图像检索', p, 14);

  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, sort_order) VALUES
  (t, 'audio', '音频', 2) RETURNING id INTO p;
  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, parent_id, sort_order) VALUES
  (t, 'asr', '语音识别', p, 0), (t, 'tts', '语音合成', p, 1),
  (t, 'speaker-verification', '说话人验证', p, 2), (t, 'audio-classification', '音频分类', p, 3),
  (t, 'music-generation', '音乐生成', p, 4), (t, 'speech-translation', '语音翻译', p, 5),
  (t, 'voiceprint-recognition', '声纹识别', p, 6), (t, 'speech-emotion-recognition', '语音情绪识别', p, 7),
  (t, 'keyword-detection', '关键词检测', p, 8);

  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, sort_order) VALUES
  (t, 'video', '视频', 3) RETURNING id INTO p;
  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, parent_id, sort_order) VALUES
  (t, 'video-classification', '视频分类', p, 0), (t, 'video-generation', '视频生成', p, 1),
  (t, 'action-recognition', '动作识别', p, 2), (t, 'video-understanding', '视频理解', p, 3),
  (t, 'video-qa', '视频问答', p, 4), (t, 'video-captioning', '视频字幕', p, 5),
  (t, 'object-tracking', '目标跟踪', p, 6), (t, 'video-segmentation', '视频分割', p, 7),
  (t, 'video-retrieval', '视频检索', p, 8);

  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, sort_order) VALUES
  (t, 'multimodal', '多模态', 4) RETURNING id INTO p;
  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, parent_id, sort_order) VALUES
  (t, 'image-text-matching-mm', '图文匹配', p, 0), (t, 'visual-qa', '视觉问答', p, 1),
  (t, 'visual-reasoning', '视觉推理', p, 2), (t, 'multimodal-dialogue', '多模态对话', p, 3),
  (t, 'image-text-understanding', '图文理解', p, 4), (t, 'video-understanding-mm', '视频理解', p, 5),
  (t, 'unified-multimodal', '统一多模态', p, 6);

  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, sort_order) VALUES
  (t, 'embodied-ai', '具身智能', 5) RETURNING id INTO p;
  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, parent_id, sort_order) VALUES
  (t, 'robot-manipulation', '机器人操作', p, 0), (t, 'navigation-planning', '导航规划', p, 1);

  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, sort_order) VALUES
  (t, 'scientific', '科学计算', 6) RETURNING id INTO p;
  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, parent_id, sort_order) VALUES
  (t, 'protein-structure-gen', '蛋白质结构生成', p, 0), (t, 'molecule-generation', '分子生成', p, 1);

  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, sort_order) VALUES
  (t, 'biomedical', '生物医学', 7) RETURNING id INTO p;
  INSERT INTO taxonomy_values (taxonomy_id, value_key, display_name, parent_id, sort_order) VALUES
  (t, 'medical-imaging', '医学影像', p, 0), (t, 'clinical-text', '临床文本', p, 1),
  (t, 'protein-structure-gen-bio', '蛋白质结构生成', p, 2);
END $$;
