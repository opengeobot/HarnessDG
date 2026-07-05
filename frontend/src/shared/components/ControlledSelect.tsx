/**
 * 功能: 受控下拉选择器。从 API 端点获取选项列表，支持搜索与 loading 状态。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
import { useQuery } from '@tanstack/react-query';
import { Select, Spin, type SelectProps } from 'antd';
import { apiClient } from '@/shared/api';

interface ControlledSelectProps extends Omit<SelectProps, 'options' | 'loading'> {
  /** API 路径（相对 /api/v1），例如 /system/tags */
  apiUrl: string;
  /** React Query 缓存键 */
  queryKey: string | string[];
  /** 从 API 响应中提取选项列表 */
  extractOptions: (data: unknown) => SelectOption[];
  /** 额外查询参数（例如组织变更时刷新项目列表） */
  enabled?: boolean;
}

export interface SelectOption {
  value: string;
  label: string;
}

/**
 * 受控下拉选择器。
 *
 * <p>从指定 API 端点获取选项列表，支持搜索过滤与 loading 状态展示。
 * 替换自由输入（mode="tags"）为受控 API 选择器，防止无效值提交。
 */
export function ControlledSelect({
  apiUrl,
  queryKey,
  extractOptions,
  enabled = true,
  ...selectProps
}: ControlledSelectProps) {
  const { data, isLoading } = useQuery({
    queryKey: Array.isArray(queryKey) ? queryKey : [queryKey],
    queryFn: () => apiClient.get<unknown>(apiUrl),
    select: extractOptions,
    enabled,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });

  return (
    <Select
      showSearch
      filterOption={(input, option) =>
        (option?.label ?? '').toLowerCase().includes(input.toLowerCase())
      }
      notFoundContent={isLoading ? <Spin size="small" /> : undefined}
      loading={isLoading}
      options={data ?? []}
      {...selectProps}
    />
  );
}
