/**
 * 功能：字典标签展示 - 根据字典项的 color/icon 渲染 AntD Tag
 * 时间：2026-05-07
 * 作者：AxeXie
 */
import { Tag, type TagProps } from 'antd';
import { useDictionary } from '@/hooks/useDictionary';

export interface DictTagProps extends Omit<TagProps, 'color'> {
  groupCode: string;
  code: string | null | undefined;
  fallback?: string;
}

export function DictTag({ groupCode, code, fallback = '-', children, ...rest }: DictTagProps) {
  const { getLabel, getColor } = useDictionary(groupCode);
  if (!code) return <Tag {...rest}>{fallback}</Tag>;
  const label = getLabel(code);
  const color = getColor(code);
  return (
    <Tag color={color || undefined} {...rest}>
      {children ?? label}
    </Tag>
  );
}

export default DictTag;
