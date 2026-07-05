/**
 * 功能: 资产目录页面。模型/数据集切换、关键词检索、列表展示、登记与删除，
 *       经统一 API Client 访问后端，权限判断以后端为准。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
import { useMemo, useState } from 'react';
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
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
import { useTranslation } from 'react-i18next';
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
  const { t } = useTranslation();
  useDocumentTitle(t('assets.title'));
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
      message.success(t('assets.deleted'));
      void queryClient.invalidateQueries({ queryKey: ['assets'] });
    },
    onError: (error) => {
      message.error(isApiError(error) ? error.message : t('assets.deleteFailed'));
    },
  });

  const items = useMemo(
    () => query.data?.pages.flatMap((page) => page.items) ?? [],
    [query.data],
  );

  const columns: ColumnsType<AssetSummary> = [
    {
      title: t('assets.columns.name'),
      dataIndex: 'name',
      key: 'name',
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Link to={`/assets/${record.assetId}`}>
            <Typography.Text strong>{record.displayName || record.name}</Typography.Text>
          </Link>
          <Typography.Text type="secondary" code>
            {record.namespace}/{record.name}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: t('assets.columns.type'),
      dataIndex: 'type',
      key: 'type',
      render: (type: AssetType) => (
        <Tag color={type === 'MODEL' ? 'geekblue' : 'purple'}>
          {type === 'MODEL' ? t('assets.model') : t('assets.dataset')}
        </Tag>
      ),
    },
    {
      title: t('assets.columns.spec'),
      key: 'spec',
      render: (_, record) =>
        record.type === 'MODEL'
          ? [record.framework, record.task].filter(Boolean).join(' · ') || '-'
          : [record.format, record.modality].filter(Boolean).join(' · ') || '-',
    },
    {
      title: t('assets.columns.visibility'),
      dataIndex: 'visibility',
      key: 'visibility',
      render: (visibility: string) => (
        <Tag color={VISIBILITY_COLOR[visibility]}>{visibility}</Tag>
      ),
    },
    {
      title: t('assets.columns.status'),
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => <Tag color={STATUS_COLOR[status]}>{status}</Tag>,
    },
    {
      title: t('assets.columns.tags'),
      key: 'tags',
      render: (_, record) => (
        <Space size={[0, 4]} wrap>
          {record.tags?.map((tag) => (
            <Tag key={tag}>{tag}</Tag>
          ))}
          {record.tagIds?.map((tagId) => (
            <Tag key={tagId} color="blue">{tagId}</Tag>
          ))}
        </Space>
      ),
    },
    {
      title: t('assets.columns.action'),
      key: 'action',
      render: (_, record) => (
        <Popconfirm
          title={t('assets.confirmDelete')}
          okText={t('common.delete')}
          cancelText={t('common.cancel')}
          onConfirm={() => deleteMutation.mutate(record.assetId)}
        >
          <Button danger type="link" size="small">
            {t('common.delete')}
          </Button>
        </Popconfirm>
      ),
    },
  ];

  return (
    <Flex vertical gap={16}>
      <Flex justify="space-between" align="center" wrap gap={12}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {t('assets.title')}
        </Typography.Title>
        <Space wrap>
          <Segmented<TypeFilter>
            value={typeFilter}
            onChange={(value) => setTypeFilter(value)}
            options={[
              { value: 'ALL', label: t('assets.all') },
              { value: 'MODEL', label: t('assets.model') },
              { value: 'DATASET', label: t('assets.dataset') },
            ]}
          />
          <Input.Search
            allowClear
            placeholder={t('assets.searchPlaceholder')}
            style={{ width: 260 }}
            onSearch={(value) => setKeyword(value.trim())}
          />
          <Button onClick={() => query.refetch()}>{t('common.refresh')}</Button>
          <Button type="primary" onClick={() => setCreateOpen(true)}>
            {t('assets.registerAsset')}
          </Button>
        </Space>
      </Flex>

      <Table<AssetSummary>
        rowKey="assetId"
        columns={columns}
        dataSource={items}
        loading={query.isLoading}
        pagination={false}
        locale={{ emptyText: <Empty description={t('assets.emptyText')} /> }}
      />

      {query.hasNextPage ? (
        <Flex justify="center">
          <Button onClick={() => query.fetchNextPage()} loading={query.isFetchingNextPage}>
            {t('assets.loadMore')}
          </Button>
        </Flex>
      ) : null}

      <CreateAssetModal open={createOpen} onClose={() => setCreateOpen(false)} />
    </Flex>
  );
}
