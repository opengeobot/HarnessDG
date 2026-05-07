/**
 * 功能：字典单选按钮组
 * 时间：2026-05-07
 * 作者：AxeXie
 */
import { Radio, type RadioGroupProps } from 'antd';
import { useDictionary } from '@/hooks/useDictionary';

export interface DictRadioGroupProps extends Omit<RadioGroupProps, 'options'> {
  groupCode: string;
  valueField?: 'code' | 'value';
  /** 渲染为 Button 样式而不是圆形 Radio */
  buttonStyle?: 'outline' | 'solid';
  optionType?: 'default' | 'button';
}

export function DictRadioGroup({ groupCode, valueField = 'code', ...rest }: DictRadioGroupProps) {
  const { items, getLabel } = useDictionary(groupCode);
  const options = items
    .filter((it) => it.status !== 'inactive')
    .map((it) => ({
      label: getLabel(it.code),
      value: valueField === 'value' ? it.value : it.code,
    }));
  return <Radio.Group options={options} {...rest} />;
}

export default DictRadioGroup;
