/**
 * 功能: 模型档案字段候选项——创建表单与资产设置页共用，避免自由输入非法值。
 * 时间: 2026-10-13
 * 作者: AxeXie
 */

const toOptions = (values: readonly string[]) =>
  values.map((value) => ({ value, label: value }));

/** 模型架构 */
export const ARCHITECTURE_OPTIONS = toOptions([
  'decoder-only',
  'encoder-only',
  'encoder-decoder',
  'moe',
  'diffusion',
]);

/** 参数规模 */
export const PARAMETER_SCALE_OPTIONS = toOptions([
  '0.5B',
  '1B',
  '1.5B',
  '3B',
  '7B',
  '13B',
  '34B',
  '70B',
]);

/** 数值精度（ModelProfile.precision 为单值字符串） */
export const PRECISION_OPTIONS = toOptions(['fp32', 'fp16', 'bf16', 'int8', 'int4']);

/** 权重格式 */
export const WEIGHT_FORMAT_OPTIONS = toOptions([
  'safetensors',
  'bin',
  'pt',
  'onnx',
  'gguf',
]);

/** 推理运行时 */
export const RUNTIME_OPTIONS = toOptions([
  'vllm',
  'transformers',
  'onnxruntime',
  'llama.cpp',
  'tensorrt-llm',
]);
