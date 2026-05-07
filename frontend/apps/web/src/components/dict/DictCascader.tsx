/**
 * 功能：基于 Ant Design Cascader 的字典级联选择器，用于 is_tree=true 的字典
 * 时间：2026-05-07
 * 作者：AxeXie
 */
import { Cascader } from 'antd';
import { useMemo } from 'react';
import type { DictionaryItem } from '@harnessdg/shared';
import { useDictionary } from '@/hooks/useDictionary';

interface CascaderOption {
  value: string;
  label: string;
  children?: CascaderOption[];
}

export interface DictCascaderProps {
  groupCode: string;
  valueField?: 'code' | 'value';
  value?: any;
  onChange?: (value: any, selectedOptions: any) => void;
  placeholder?: string;
  multiple?: boolean;
  allowClear?: boolean;
  disabled?: boolean;
  style?: React.CSSProperties;
  className?: string;
}

function buildTree(items: DictionaryItem[], locale: string, valueField: 'code' | 'value'): CascaderOption[] {
  const map = new Map<string | number, CascaderOption & { __raw: DictionaryItem }>();
  const roots: CascaderOption[] = [];
  items.forEach((it) => {
    const opt: any = {
      value: valueField === 'value' ? it.value : it.code,
      label: (it.label as any)?.[locale] || (it.label as any)?.zh_CN || it.code,
      __raw: it,
    };
    map.set(it.id, opt);
  });
  items.forEach((it) => {
    const opt = map.get(it.id)!;
    if (it.parentId) {
      const parent = map.get(it.parentId);
      if (parent) {
        (parent.children ||= []).push(opt);
      } else {
        roots.push(opt);
      }
    } else {
      roots.push(opt);
    }
  });
  return roots;
}

export function DictCascader({ groupCode, valueField = 'code', ...rest }: DictCascaderProps) {
  const { items, loading } = useDictionary(groupCode);
  const locale = typeof window !== 'undefined'
    ? localStorage.getItem('harnessdg_locale') || 'zh_CN'
    : 'zh_CN';
  const options = useMemo(() => buildTree(items, locale, valueField), [items, locale, valueField]);

  return <Cascader options={options as any} loading={loading} showSearch {...(rest as any)} />;
}

export default DictCascader;
