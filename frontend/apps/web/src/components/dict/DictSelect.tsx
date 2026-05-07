/**
 * 功能：基于 Ant Design Select 的字典下拉选择器，支持 i18n/搜索/多选
 * 时间：2026-05-07
 * 作者：AxeXie
 */
import { Select, type SelectProps } from 'antd';
import { useDictionary } from '@/hooks/useDictionary';

export interface DictSelectProps extends Omit<SelectProps, 'options' | 'loading'> {
  groupCode: string;
  /** 值字段：默认使用字典项的 code，设为 'value' 则使用 value 字段 */
  valueField?: 'code' | 'value';
}

export function DictSelect({
  groupCode,
  valueField = 'code',
  showSearch = true,
  allowClear = true,
  ...rest
}: DictSelectProps) {
  const { items, loading, getLabel } = useDictionary(groupCode);

  const options = items
    .filter((it) => it.status !== 'inactive')
    .map((it) => ({
      label: getLabel(it.code),
      value: valueField === 'value' ? it.value : it.code,
    }));

  return (
    <Select
      options={options}
      loading={loading}
      showSearch={showSearch}
      allowClear={allowClear}
      optionFilterProp="label"
      {...rest}
    />
  );
}

export default DictSelect;
