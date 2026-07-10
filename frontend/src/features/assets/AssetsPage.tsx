/**
 * 功能: 资产目录页面。模型/数据集切换、关键词检索、列表展示、登记与删除，
 *       经统一 API Client 访问后端，权限判断以后端为准。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
import { useMemo, useState } from 'react';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import {
  Card,
  App,
  Button,
  Empty,
  Flex,
  Input,
  Popconfirm,
  Segmented,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useTranslation } from 'react-i18next';
import { useDocumentTitle } from '@/shared/hooks';
import { isApiError } from '@/shared/api';
import { ControlledSelect, type SelectOption } from '@/shared/components/ControlledSelect';
import { deleteAsset, getAssetFacets, searchAssets } from './api';
import { CreateAssetModal } from './CreateAssetModal';
import type { AssetFacetView, AssetStatus, AssetSummary, AssetType, Visibility } from './types';

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
  const [searchParams, setSearchParams] = useSearchParams();

  const [typeFilter, setTypeFilter] = useState<TypeFilter>(
    (searchParams.get('type') as TypeFilter) || 'ALL',
  );
  const [keyword, setKeyword] = useState(searchParams.get('keyword') ?? '');
  const [createOpen, setCreateOpen] = useState(false);
  const [language, setLanguage] = useState<string | undefined>(searchParams.get('language') ?? undefined);
  const [sensitivity, setSensitivity] = useState<string | undefined>(searchParams.get('sensitivity') ?? undefined);
  const [visibility, setVisibility] = useState<Visibility | undefined>(
    (searchParams.get('visibility') as Visibility) ?? undefined,
  );
  const [status, setStatus] = useState<AssetStatus | undefined>(
    (searchParams.get('status') as AssetStatus) ?? undefined,
  );
  const [teamId, setTeamId] = useState<string | undefined>(searchParams.get('teamId') ?? undefined);
  const [organizationId, setOrganizationId] = useState<string | undefined>(
    searchParams.get('organizationId') ?? undefined,
  );

  // URL 同步
  const updateParams = (updates: Record<string, string | undefined>) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      for (const [k, v] of Object.entries(updates)) {
        if (v) next.set(k, v);
        else next.delete(k);
      }
      return next;
    }, { replace: true });
  };

  const queryType = typeFilter === 'ALL' ? undefined : typeFilter;

  const facetsQuery = useQuery<AssetFacetView>({
    queryKey: ['asset-facets', { type: queryType, keyword }],
    queryFn: () => getAssetFacets(keyword || undefined, queryType),
  });

  const facetSections = useMemo(() => {
    const facets = facetsQuery.data;
    if (!facets) return [];
    const sections: Array<{ key: string; label: string; counts: Record<string, number> }> = [];
    if (queryType === 'MODEL' || !queryType) {
      sections.push(
        { key: 'frameworks', label: t('assets.facets.frameworks'), counts: facets.frameworks },
        { key: 'tasks', label: t('assets.facets.tasks'), counts: facets.tasks },
      );
    }
    if (queryType === 'DATASET' || !queryType) {
      sections.push(
        { key: 'formats', label: t('assets.facets.formats'), counts: facets.formats },
        { key: 'modalities', label: t('assets.facets.modalities'), counts: facets.modalities },
      );
    }
    sections.push(
      { key: 'licenses', label: t('assets.facets.licenses'), counts: facets.licenses },
      { key: 'sensitivities', label: t('assets.facets.sensitivities'), counts: facets.sensitivities },
    );
    return sections.filter((s) => Object.keys(s.counts).length > 0);
  }, [facetsQuery.data, queryType, t]);

  const query = useInfiniteQuery({
    queryKey: ['assets', { type: queryType, keyword, language, sensitivity, visibility, status, teamId, organizationId }],
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam }) =>
      searchAssets({
        type: queryType,
        keyword: keyword || undefined,
        language: language || undefined,
        sensitivity: sensitivity || undefined,
        visibility,
        status,
        teamId,
        organizationId,
        cursor: pageParam,
        limit: 10,
      }),
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
          {record.matchedFields && record.matchedFields.length > 0 ? (
            <Space size={[4, 4]} wrap>
              {record.matchedFields.map((field) => (
                <Tag key={field} color="cyan">
                  {t(`assets.facets.matchedField.${field}`, field)}
                </Tag>
              ))}
            </Space>
          ) : null}
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

      <Flex gap={8} wrap align="center">
        <ControlledSelect
          apiUrl="/system/organizations"
          queryKey="filter-organizations"
          extractOptions={(data) =>
            (data as Array<{ organizationId: string; name: string }>).map(
              (o): SelectOption => ({ value: o.organizationId, label: o.name }),
            )
          }
          placeholder={t('assets.create.org')}
          allowClear
          style={{ width: 180 }}
          value={organizationId}
          onChange={(v: string) => {
            setOrganizationId(v ?? undefined);
            updateParams({ organizationId: v });
          }}
        />
        <Select
          placeholder={t('assets.filter.visibility')}
          allowClear
          style={{ width: 160 }}
          value={visibility}
          onChange={(v: Visibility) => {
            setVisibility(v);
            updateParams({ visibility: v });
          }}
          options={[
            { value: 'PRIVATE', label: t('assets.create.private') },
            { value: 'INTERNAL', label: t('assets.create.internal') },
            { value: 'PUBLIC', label: t('assets.create.public') },
          ]}
        />
        <Select
          placeholder={t('assets.filter.status')}
          allowClear
          style={{ width: 160 }}
          value={status}
          onChange={(v: AssetStatus) => {
            setStatus(v);
            updateParams({ status: v });
          }}
          options={[
            { value: 'ACTIVE', label: 'ACTIVE' },
            { value: 'DEPRECATED', label: 'DEPRECATED' },
            { value: 'ARCHIVED', label: 'ARCHIVED' },
          ]}
        />
        <ControlledSelect
          apiUrl="/system/dictionaries/language/items"
          queryKey="dict-language"
          extractOptions={(data) =>
            (data as Array<{ itemCode: string }>).map(
              (item): SelectOption => ({ value: item.itemCode, label: item.itemCode }),
            )
          }
          placeholder={t('assets.filter.language')}
          allowClear
          style={{ width: 160 }}
          value={language}
          onChange={(v: string) => {
            setLanguage(v ?? undefined);
            updateParams({ language: v });
          }}
        />
        <Select
          placeholder={t('assets.filter.sensitivity')}
          allowClear
          style={{ width: 160 }}
          value={sensitivity}
          onChange={(v) => {
            setSensitivity(v);
            updateParams({ sensitivity: v });
          }}
          options={[
            { value: 'PUBLIC', label: t('assets.sensitivity.PUBLIC') },
            { value: 'INTERNAL', label: t('assets.sensitivity.INTERNAL') },
            { value: 'CONFIDENTIAL', label: t('assets.sensitivity.CONFIDENTIAL') },
            { value: 'SECRET', label: t('assets.sensitivity.SECRET') },
          ]}
        />
        {organizationId && (
          <ControlledSelect
            apiUrl={`/system/organizations/${organizationId}/teams`}
            queryKey={['filter-teams', organizationId]}
            enabled={!!organizationId}
            extractOptions={(data) =>
              (data as Array<{ teamId: string; name: string }>).map(
                (team): SelectOption => ({ value: team.teamId, label: team.name }),
              )
            }
            placeholder={t('assets.filter.team')}
            allowClear
            style={{ width: 180 }}
            value={teamId}
            onChange={(v: string) => {
              setTeamId(v ?? undefined);
              updateParams({ teamId: v });
            }}
          />
        )}
      </Flex>
      <Flex gap={16} align="flex-start" wrap="wrap">
        <Card
          title={t('assets.facets.title')}
          size="small"
          style={{ width: 240, flexShrink: 0 }}
          loading={facetsQuery.isLoading}
        >
          <Typography.Text type="secondary">
            {t('assets.facets.total', { count: facetsQuery.data?.totalCount ?? 0 })}
          </Typography.Text>
          <Flex vertical gap={12} style={{ marginTop: 12 }}>
            {facetSections.map((section) => (
              <div key={section.key}>
                <Typography.Text strong>{section.label}</Typography.Text>
                <Flex vertical gap={4} style={{ marginTop: 4 }}>
                  {Object.entries(section.counts)
                    .sort(([, a], [, b]) => b - a)
                    .slice(0, 8)
                    .map(([value, count]) => (
                      <Flex key={value} justify="space-between">
                        <Typography.Text>{value}</Typography.Text>
                        <Tag>{count}</Tag>
                      </Flex>
                    ))}
                </Flex>
              </div>
            ))}
            {facetSections.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('assets.facets.empty')} />
            ) : null}
          </Flex>
        </Card>
        <Flex vertical gap={8} style={{ flex: 1, minWidth: 480 }}>
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
        </Flex>
      </Flex>

      <CreateAssetModal open={createOpen} onClose={() => setCreateOpen(false)} />
    </Flex>
  );
}
