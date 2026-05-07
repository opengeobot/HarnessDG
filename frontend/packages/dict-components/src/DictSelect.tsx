/**
 * 功能：字典下拉选择组件
 * 时间：2026-05-07
 * 作者：AxeXie
 */
import { Select, type SelectProps } from 'antd';
import { useTranslation } from 'react-i18next';
import { useDictionary } from './useDictionary';

interface DictSelectProps extends Omit<SelectProps, 'options' | 'loading'> {
  groupCode: string;
}

export function DictSelect({ groupCode, ...restProps }: DictSelectProps) {
  const { items, loading } = useDictionary(groupCode);
  const { i18n } = useTranslation();
  const locale = i18n.language || 'zh_CN';

  const options = items.map((item) => ({
    label: item.label[locale] || item.label['zh_CN'],
    value: item.value,
  }));

  return <Select options={options} loading={loading} {...restProps} />;
}
