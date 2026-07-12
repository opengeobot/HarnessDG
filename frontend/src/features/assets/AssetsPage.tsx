/**
 * 功能: 资产目录页面。模型/数据集切换、关键词检索、列表展示、登记与删除，
 *       经统一 API Client 访问后端，权限判断以后端为准。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
import { useMemo, useState, type Dispatch, type SetStateAction } from 'react';
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
  const [framework, setFramework] = useState<string | undefined>(searchParams.get('framework') ?? undefined);
  const [task, setTask] = useState<string | undefined>(searchParams.get('task') ?? undefined);
  const [format, setFormat] = useState<string | undefined>(searchParams.get('format') ?? undefined);
  const [modality, setModality] = useState<string | undefined>(searchParams.get('modality') ?? undefined);
  const multiParam = (key: string) =>
    searchParams.getAll(key).length > 0 ? searchParams.getAll(key) : undefined;
  const [taskCodes, setTaskCodes] = useState<string[] | undefined>(multiParam('taskCodes'));
  const [modalityCodes, setModalityCodes] = useState<string[] | undefined>(multiParam('modalityCodes'));
  const [formatCodes, setFormatCodes] = useState<string[] | undefined>(multiParam('formatCodes'));
  const [languageCodes, setLanguageCodes] = useState<string[] | undefined>(multiParam('languageCodes'));

  // URL 同步
  const updateParams = (updates: Record<string, string | string[] | undefined>) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      for (const [k, v] of Object.entries(updates)) {
        next.delete(k);
        if (Array.isArray(v)) {
          for (const item of v) next.append(k, item);
        } else if (v) {
          next.set(k, v);
        }
      }
      return next;
    }, { replace: true });
  };

  const toggleMulti = (
    current: string[] | undefined,
    setter: Dispatch<SetStateAction<string[] | undefined>>,
    key: string,
    value: string,
  ) => {
    const arr = current ?? [];
    const next = arr.includes(value) ? arr.filter((v) => v !== value) : [...arr, value];
    const nextVal = next.length > 0 ? next : undefined;
    setter(nextVal);
    updateParams({ [key]: nextVal });
  };

  const queryType = typeFilter === 'ALL' ? undefined : typeFilter;

  const facetsQuery = useQuery<AssetFacetView>({
    queryKey: ['asset-facets', { type: queryType, keyword }],
    queryFn: () => getAssetFacets(keyword || undefined, queryType),
  });

  const facetSections = useMemo(() => {
    const facets = facetsQuery.data;
    if (!facets) return [];
    type Section = {
      key: string;
      label: string;
      counts: Record<string, number>;
      paramKey: string;
      multi: boolean;
    };
    const sections: Section[] = [];
    if (queryType === 'MODEL' || !queryType) {
      sections.push(
        { key: 'frameworks', label: t('assets.facets.frameworks'), counts: facets.frameworks, paramKey: 'framework', multi: false },
        { key: 'tasks', label: t('assets.facets.tasks'), counts: facets.tasks, paramKey: 'task', multi: false },
      );
    }
    if (queryType === 'DATASET' || !queryType) {
      sections.push(
        { key: 'formats', label: t('assets.facets.formats'), counts: facets.formats, paramKey: 'format', multi: false },
        { key: 'modalities', label: t('assets.facets.modalities'), counts: facets.modalities, paramKey: 'modality', multi: false },
        { key: 'taskCodes', label: t('assets.facets.taskCodes'), counts: facets.taskCodes, paramKey: 'taskCodes', multi: true },
        { key: 'modalityCodes', label: t('assets.facets.modalityCodes'), counts: facets.modalityCodes, paramKey: 'modalityCodes', multi: true },
        { key: 'formatCodes', label: t('assets.facets.formatCodes'), counts: facets.formatCodes, paramKey: 'formatCodes', multi: true },
        { key: 'languageCodes', label: t('assets.facets.languageCodes'), counts: facets.languageCodes, paramKey: 'languageCodes', multi: true },
      );
    }
    sections.push(
      { key: 'sensitivities', label: t('assets.facets.sensitivities'), counts: facets.sensitivities, paramKey: 'sensitivity', multi: false },
    );
    return sections.filter((s) => Object.keys(s.counts).length > 0);
  }, [facetsQuery.data, queryType, t]);

  const isFacetActive = (section: { paramKey: string; multi: boolean }, value: string) => {
    switch (section.paramKey) {
      case 'framework': return framework === value;
      case 'task': return task === value;
      case 'format': return format === value;
      case 'modality': return modality === value;
      case 'sensitivity': return sensitivity === value;
      case 'taskCodes': return taskCodes?.includes(value) ?? false;
      case 'modalityCodes': return modalityCodes?.includes(value) ?? false;
      case 'formatCodes': return formatCodes?.includes(value) ?? false;
      case 'languageCodes': return languageCodes?.includes(value) ?? false;
      default: return false;
    }
  };

  const onFacetClick = (section: { paramKey: string; multi: boolean }, value: string) => {
    switch (section.paramKey) {
      case 'framework': {
        const next = framework === value ? undefined : value;
        setFramework(next); updateParams({ framework: next });
        break;
      }
      case 'task': {
        const next = task === value ? undefined : value;
        setTask(next); updateParams({ task: next });
        break;
      }
      case 'format': {
        const next = format === value ? undefined : value;
        setFormat(next); updateParams({ format: next });
        break;
      }
      case 'modality': {
        const next = modality === value ? undefined : value;
        setModality(next); updateParams({ modality: next });
        break;
      }
      case 'sensitivity': {
        const next = sensitivity === value ? undefined : value;
        setSensitivity(next); updateParams({ sensitivity: next });
        break;
      }
      case 'taskCodes': toggleMulti(taskCodes, setTaskCodes, 'taskCodes', value); break;
      case 'modalityCodes': toggleMulti(modalityCodes, setModalityCodes, 'modalityCodes', value); break;
      case 'formatCodes': toggleMulti(formatCodes, setFormatCodes, 'formatCodes', value); break;
      case 'languageCodes': toggleMulti(languageCodes, setLanguageCodes, 'languageCodes', value); break;
    }
  };

  const query = useInfiniteQuery({
    queryKey: ['assets', { type: queryType, keyword, language, sensitivity, visibility, status, teamId, organizationId, framework, task, format, modality, taskCodes, modalityCodes, formatCodes, languageCodes }],
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
        framework: framework || undefined,
        task: task || undefined,
        format: format || undefined,
        modality: modality || undefined,
        taskCodes,
        modalityCodes,
        formatCodes,
        languageCodes,
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
                    .map(([value, count]) => {
                      const active = isFacetActive(section, value);
                      return (
                        <Flex
                          key={value}
                          justify="space-between"
                          style={{ cursor: 'pointer', padding: '2px 4px', borderRadius: 4,
                            background: active ? 'rgba(22,119,255,0.08)' : 'transparent' }}
                          onClick={() => onFacetClick(section, value)}
                        >
                          <Typography.Text strong={active}>{value}</Typography.Text>
                          <Tag color={active ? 'blue' : 'default'}>{count}</Tag>
                        </Flex>
                      );
                    })}
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
