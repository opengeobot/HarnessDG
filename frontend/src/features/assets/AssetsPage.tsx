/**
 * 功能: 资产目录页面。模型/数据集切换、关键词检索、列表展示、登记与删除，
 *       经统一 API Client 访问后端，权限判断以后端为准。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
import { useMemo, useState } from 'react';
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Button,
  Empty,
  Flex,
  Input,
  Popconfirm,
  Segmented,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useDocumentTitle } from '@/shared/hooks';
import { isApiError } from '@/shared/api';
import { deleteAsset, searchAssets } from './api';
import { CreateAssetModal } from './CreateAssetModal';
import type { AssetSummary, AssetType } from './types';

type TypeFilter = 'ALL' | AssetType;

const VISIBILITY_COLOR: Record<string, string> = {
  PRIVATE: 'red',
  INTERNAL: 'blue',
  PUBLIC: 'green',
};

const STATUS_COLOR: Record<string, string> = {
  ACTIVE: 'green',
  DEPRECATED: 'orange',
  ARCHIVED: 'default',
};

export function AssetsPage() {
  useDocumentTitle('资产目录');
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  const [typeFilter, setTypeFilter] = useState<TypeFilter>('ALL');
  const [keyword, setKeyword] = useState('');
  const [createOpen, setCreateOpen] = useState(false);

  const queryType = typeFilter === 'ALL' ? undefined : typeFilter;

  const query = useInfiniteQuery({
    queryKey: ['assets', { type: queryType, keyword }],
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam }) =>
      searchAssets({ type: queryType, keyword: keyword || undefined, cursor: pageParam, limit: 10 }),
    getNextPageParam: (lastPage) => (lastPage.hasMore ? lastPage.nextCursor ?? undefined : undefined),
  });

  const deleteMutation = useMutation({
    mutationFn: (assetId: string) => deleteAsset(assetId),
    onSuccess: () => {
      message.success('已删除资产');
      void queryClient.invalidateQueries({ queryKey: ['assets'] });
    },
    onError: (error) => {
      message.error(isApiError(error) ? error.message : '删除失败');
    },
  });

  const items = useMemo(
    () => query.data?.pages.flatMap((page) => page.items) ?? [],
    [query.data],
  );

  const columns: ColumnsType<AssetSummary> = [
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{record.displayName || record.name}</Typography.Text>
          <Typography.Text type="secondary" code>
            {record.namespace}/{record.name}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      render: (type: AssetType) => (
        <Tag color={type === 'MODEL' ? 'geekblue' : 'purple'}>
          {type === 'MODEL' ? '模型' : '数据集'}
        </Tag>
      ),
    },
    {
      title: '规格',
      key: 'spec',
      render: (_, record) =>
        record.type === 'MODEL'
          ? [record.framework, record.task].filter(Boolean).join(' · ') || '-'
          : [record.format, record.modality].filter(Boolean).join(' · ') || '-',
    },
    {
      title: '可见性',
      dataIndex: 'visibility',
      key: 'visibility',
      render: (visibility: string) => (
        <Tag color={VISIBILITY_COLOR[visibility]}>{visibility}</Tag>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => <Tag color={STATUS_COLOR[status]}>{status}</Tag>,
    },
    {
      title: '标签',
      dataIndex: 'tags',
      key: 'tags',
      render: (tags: string[]) => (
        <Space size={[0, 4]} wrap>
          {tags.map((tag) => (
            <Tag key={tag}>{tag}</Tag>
          ))}
        </Space>
      ),
    },
    {
      title: '操作',
      key: 'action',
      render: (_, record) => (
        <Popconfirm
          title="确认删除该资产？"
          okText="删除"
          cancelText="取消"
          onConfirm={() => deleteMutation.mutate(record.assetId)}
        >
          <Button danger type="link" size="small">
            删除
          </Button>
        </Popconfirm>
      ),
    },
  ];

  return (
    <Flex vertical gap={16}>
      <Flex justify="space-between" align="center" wrap gap={12}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          资产目录
        </Typography.Title>
        <Space wrap>
          <Segmented<TypeFilter>
            value={typeFilter}
            onChange={(value) => setTypeFilter(value)}
            options={[
              { value: 'ALL', label: '全部' },
              { value: 'MODEL', label: '模型' },
              { value: 'DATASET', label: '数据集' },
            ]}
          />
          <Input.Search
            allowClear
            placeholder="搜索名称、描述或标签"
            style={{ width: 260 }}
            onSearch={(value) => setKeyword(value.trim())}
          />
          <Button onClick={() => query.refetch()}>刷新</Button>
          <Button type="primary" onClick={() => setCreateOpen(true)}>
            登记资产
          </Button>
        </Space>
      </Flex>

      <Table<AssetSummary>
        rowKey="assetId"
        columns={columns}
        dataSource={items}
        loading={query.isLoading}
        pagination={false}
        locale={{ emptyText: <Empty description="暂无资产，点击“登记资产”开始" /> }}
      />

      {query.hasNextPage ? (
        <Flex justify="center">
          <Button onClick={() => query.fetchNextPage()} loading={query.isFetchingNextPage}>
            加载更多
          </Button>
        </Flex>
      ) : null}

      <CreateAssetModal open={createOpen} onClose={() => setCreateOpen(false)} />
    </Flex>
  );
}
