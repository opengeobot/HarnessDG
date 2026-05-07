/**
 * 功能：字典标签展示组件
 * 时间：2026-05-07
 * 作者：AxeXie
 */
import { Tag } from 'antd';
import { useDictionary } from './useDictionary';

interface DictTagProps {
  groupCode: string;
  code: string;
}

export function DictTag({ groupCode, code }: DictTagProps) {
  const { getLabel, getColor } = useDictionary(groupCode);

  const label = getLabel(code);
  const color = getColor(code);

  return <Tag color={color || undefined}>{label}</Tag>;
}
