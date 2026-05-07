/**
 * 功能：共享类型定义 - API 响应、字典、本体、任务
 * 时间：2026-05-07
 * 作者：AxeXie
 */

// === API 统一响应 ===
export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
  traceId: string;
  timestamp: string;
}

export interface PagedResponse<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
}

// === 多语言文本 ===
export interface I18nText {
  zh_CN: string;
  en_US: string;
  [locale: string]: string;
}

// === 数据字典 ===
export interface DictionaryGroup {
  id: string;
  code: string;
  name: I18nText;
  description: I18nText;
  category: string;
  isTree: boolean;
  isMultiple: boolean;
  isEditable: boolean;
  status: 'active' | 'inactive';
}

export interface DictionaryItem {
  id: string;
  groupCode: string;
  parentId: string | null;
  code: string;
  label: I18nText;
  description: I18nText;
  value: string;
  icon: string | null;
  color: string | null;
  sortOrder: number;
  isDefault: boolean;
  isSystem: boolean;
  status: 'active' | 'inactive';
  extra: Record<string, unknown>;
  children?: DictionaryItem[];
}

// === 本体 ===
export interface OntologyEntity {
  id: number;
  code: string;
  name: I18nText;
  description: I18nText;
  entityType: string;
  dataDomain: string;
  owner: string;
  status: string;
  version: number;
  tags: string[];
  createdAt: string;
  updatedAt: string;
}

export interface OntologyMetric {
  id: number;
  code: string;
  name: I18nText;
  description: I18nText;
  entityId: number;
  metricType: string;
  aggMethod: string;
  expression: string;
  grain: string;
  unit: string;
  dataDomain: string;
  owner: string;
  status: string;
  version: number;
  dagsterAssetKey: string | null;
  createdAt: string;
  updatedAt: string;
}

// === 任务 ===
export interface BusinessTask {
  id: number;
  taskCode: string;
  taskType: string;
  title: I18nText;
  description: I18nText;
  status: string;
  priority: string;
  inputParams: Record<string, unknown>;
  executionPlan: Record<string, unknown> | null;
  result: Record<string, unknown> | null;
  initiator: string;
  assignee: string | null;
  dataDomain: string;
  traceId: string;
  createdAt: string;
  updatedAt: string;
}
