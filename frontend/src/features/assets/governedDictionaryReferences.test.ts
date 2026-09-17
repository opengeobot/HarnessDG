/// <reference types="vite/client" />
/**
 * 功能: 资产表单受控字典引用回归门禁——断言前端引用的 dictCode 全部存在于后端
 *       REQ-TAX-001 规定的 MVP Catalog，并锁定治理字段的受控选择器形态，
 *       防止再次出现静默漂移导致的空下拉。
 * 时间: 2026-09-17
 * 作者: AxeXie
 */
import { describe, expect, it } from 'vitest';

/**
 * REQ-TAX-001 规定的 P0-B MVP Catalog dictCode。
 *
 * 权威来源:
 *   - 需求: docs/ai-spec/01-requirements/p0b-platform-services.md（REQ-TAX-001「行为」第 2 条）
 *   - 种子: backend/src/main/resources/db/migration/V6__taxonomy_dictionary.sql
 *     （`INSERT INTO system_dict_type` 的值列表）
 *
 * 该清单在本测试中固化，作为前端一侧的契约快照：后端若新增或改名 dictCode，
 * 必须同步更新此清单并在同一变更中调整前端引用。
 */
const MVP_DICT_CODES = [
  'model_task',
  'model_framework',
  'dataset_modality',
  'dataset_format',
  'industry_tag',
  'license_catalog',
  'sensitivity_level',
  'deprecation_reason',
] as const;

/** 以原始文本形式内联所有非测试源文件，避免依赖 Node 文件系统 API。 */
const sourceFiles = import.meta.glob('../**/*.{ts,tsx}', {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>;

/** 仅保留真正的源码，排除测试与测试工具，避免把断言文本自身当作引用。 */
function productionSources(): [string, string][] {
  return Object.entries(sourceFiles).filter(
    ([path]) => !/\.test\.tsx?$/.test(path) && !path.includes('/test/'),
  );
}

/** 提取源码中所有 `/system/dictionaries/<code>/items` 形式的 dictCode 引用。 */
function referencedDictCodes(): { code: string; file: string }[] {
  const pattern = /\/system\/dictionaries\/([A-Za-z0-9_]+)\/items/g;
  const found: { code: string; file: string }[] = [];
  for (const [path, content] of productionSources()) {
    for (const match of content.matchAll(pattern)) {
      found.push({ code: match[1], file: path });
    }
  }
  return found;
}

/** 按路径取某个源文件的原始文本。 */
function sourceOf(suffix: string): string {
  const entry = Object.entries(sourceFiles).find(([path]) => path.endsWith(suffix));
  if (!entry) {
    throw new Error(`未找到源文件 ${suffix}；glob 解析结果：${Object.keys(sourceFiles).join(', ')}`);
  }
  return entry[1];
}

describe('受控字典引用门禁', () => {
  it('前端引用的每个 dictCode 都存在于 REQ-TAX-001 MVP Catalog', () => {
    const references = referencedDictCodes();
    expect(
      references.length,
      '未扫描到任何字典引用，门禁已失效（glob 可能未命中源码）',
    ).toBeGreaterThan(0);

    const unknown = references.filter(
      (ref) => !(MVP_DICT_CODES as readonly string[]).includes(ref.code),
    );

    expect(
      unknown,
      '以下字典引用不存在于 MVP Catalog，后端将返回 DICTIONARY_ITEM_NOT_FOUND，' +
        '对应下拉恒为空：\n' +
        unknown.map((ref) => `  ${ref.code}  <-  ${ref.file}`).join('\n'),
    ).toEqual([]);
  });

  it('资产表单的五个治理字段全部经受控字典选择器提交', () => {
    const modal = sourceOf('CreateAssetModal.tsx');

    for (const dictCode of [
      'license_catalog',
      'model_framework',
      'model_task',
      'dataset_format',
      'dataset_modality',
    ]) {
      expect(modal, `${dictCode} 必须经受控字典选择器提交，不得退化为自由文本`).toContain(
        `/system/dictionaries/${dictCode}/items`,
      );
    }
  });

  it('资产功能内不存在允许创建任意值的自由标签模式', () => {
    for (const [path, content] of productionSources()) {
      if (!path.includes('/features/assets/')) continue;
      expect(content, `${path} 不得使用 mode="tags"，否则可写入受控值之外的自由值`).not.toMatch(
        /mode=["']tags["']/,
      );
    }
  });

  it('资产检索页不引用无对应字典类型的失效筛选', () => {
    const page = sourceOf('AssetsPage.tsx');
    // dataset_language 不属于 P0-B MVP Catalog，且 REST 当前无法写入 language_codes，
    // 该筛选只会产生恒空结果，必须移除而非保留占位。
    expect(page).not.toContain('/system/dictionaries/language/items');
  });
});
