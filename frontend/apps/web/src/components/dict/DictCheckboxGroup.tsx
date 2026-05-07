/**
 * 功能：字典多选复选框组
 * 时间：2026-05-07
 * 作者：AxeXie
 */
import { Checkbox, type CheckboxOptionType } from 'antd';
import type { CheckboxGroupProps } from 'antd/es/checkbox';
import { useDictionary } from '@/hooks/useDictionary';

export interface DictCheckboxGroupProps extends Omit<CheckboxGroupProps, 'options'> {
  groupCode: string;
  valueField?: 'code' | 'value';
}

export function DictCheckboxGroup({
  groupCode,
  valueField = 'code',
  ...rest
}: DictCheckboxGroupProps) {
  const { items, getLabel } = useDictionary(groupCode);
  const options: CheckboxOptionType[] = items
    .filter((it) => it.status !== 'inactive')
    .map((it) => ({
      label: getLabel(it.code),
      value: valueField === 'value' ? it.value : it.code,
    }));
  return <Checkbox.Group options={options} {...rest} />;
}

export default DictCheckboxGroup;
