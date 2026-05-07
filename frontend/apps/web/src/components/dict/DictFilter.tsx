/**
 * 功能：字典筛选器 - 可多选的 Chip/Checkable Tag 列表，常用于列表页顶部过滤
 * 时间：2026-05-07
 * 作者：AxeXie
 */
import { Space, Tag, Spin } from 'antd';
import { useDictionary } from '@/hooks/useDictionary';

export interface DictFilterProps {
  groupCode: string;
  value?: string[];
  onChange?: (codes: string[]) => void;
  /** 是否包含 "全部" 选项（value=[]） */
  allowAll?: boolean;
  allLabel?: string;
  multiple?: boolean;
}

export function DictFilter({
  groupCode,
  value = [],
  onChange,
  allowAll = true,
  allLabel = '全部',
  multiple = true,
}: DictFilterProps) {
  const { items, loading, getLabel, getColor } = useDictionary(groupCode);
  if (loading) return <Spin size="small" />;

  const toggle = (code: string) => {
    if (!onChange) return;
    if (multiple) {
      const has = value.includes(code);
      onChange(has ? value.filter((c) => c !== code) : [...value, code]);
    } else {
      onChange(value[0] === code ? [] : [code]);
    }
  };

  return (
    <Space size={[8, 8]} wrap>
      {allowAll && (
        <Tag.CheckableTag checked={value.length === 0} onChange={() => onChange?.([])}>
          {allLabel}
        </Tag.CheckableTag>
      )}
      {items
        .filter((it) => it.status !== 'inactive')
        .map((it) => {
          const active = value.includes(it.code);
          const color = getColor(it.code);
          return (
            <Tag.CheckableTag
              key={it.code}
              checked={active}
              onChange={() => toggle(it.code)}
              style={active && color ? { background: color, color: '#fff', borderColor: color } : undefined}
            >
              {getLabel(it.code)}
            </Tag.CheckableTag>
          );
        })}
    </Space>
  );
}

export default DictFilter;
